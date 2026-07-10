import uuid
from datetime import timedelta

from django.conf import settings
from django.db import transaction
from django.utils import timezone
from rest_framework import generics, permissions, status
from rest_framework.exceptions import ValidationError
from rest_framework.response import Response

from apps.plans.models import Plan
from apps.subscriptions.models import Subscription
from apps.subscriptions.services.anti_sanction import (
    anti_sanction_client, generate_username, generate_password
)
from .models import Payment
from .serializers import PaymentSerializer, PaymentVerifySerializer
from .services.cafe_bazar import cafe_bazar_client


class PaymentCreateView(generics.CreateAPIView):
    """Create a pending payment record before launching Cafe Bazar IAB flow."""
    permission_classes = (permissions.IsAuthenticated,)
    serializer_class = PaymentSerializer

    def create(self, request, *args, **kwargs):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        plan = serializer.validated_data['plan']
        user = request.user

        if not plan.is_active:
            raise ValidationError("Plan is not available.")

        if plan.price_toman == 0:
            raise ValidationError("Free plans do not require payment. Use subscriptions/create/ directly.")

        payment = Payment.objects.create(
            user=user,
            plan=plan,
            amount_toman=plan.price_toman,
            gateway='cafebazar',
            status='pending',
        )
        return Response(
            PaymentSerializer(payment, context=self.get_serializer_context()).data,
            status=status.HTTP_201_CREATED,
        )


class PaymentVerifyView(generics.GenericAPIView):
    """
    Verify a Cafe Bazar purchase token, credit the user, and activate the subscription.
    
    This is called by the Android app after a successful Cafe Bazar IAB purchase.
    The app sends the purchase_token and product_id.
    Returns subscription details on success.
    """
    permission_classes = (permissions.IsAuthenticated,)
    serializer_class = PaymentVerifySerializer

    @transaction.atomic
    def post(self, request):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        purchase_token = serializer.validated_data['purchase_token']
        product_id = serializer.validated_data['product_id']
        payment_id = serializer.validated_data.get('payment_id')
        user = request.user
        package_name = settings.CAFE_BAZAR_PACKAGE_NAME

        # 1. Verify with Cafe Bazar
        verification = cafe_bazar_client.verify_purchase(package_name, product_id, purchase_token)
        if not verification:
            raise ValidationError("Purchase verification failed. Invalid or unverified purchase token.")

        purchase_state = verification.get('purchaseState', -1)
        if purchase_state != 0:  # 0 = purchased
            raise ValidationError(f"Purchase is not in valid state (state={purchase_state}).")

        consumption_state = verification.get('consumptionState', -1)
        if consumption_state != 0:  # 0 = not yet consumed
            raise ValidationError("This purchase has already been consumed.")

        # 2. Find the plan by product_id (product_id should match plan slug)
        try:
            plan = Plan.objects.get(slug=product_id, is_active=True)
        except Plan.DoesNotExist:
            raise ValidationError(f"No active plan found for product '{product_id}'.")

        # 3. Check for duplicate token usage
        if Payment.objects.filter(purchase_token=purchase_token, status='verified').exists():
            raise ValidationError("This purchase token has already been claimed.")

        # 4. Find or create payment record
        if payment_id:
            try:
                payment = Payment.objects.get(id=payment_id, user=user, status='pending')
            except Payment.DoesNotExist:
                raise ValidationError("Payment record not found or already processed.")
        else:
            payment = Payment.objects.create(
                user=user,
                plan=plan,
                amount_toman=plan.price_toman,
                gateway='cafebazar',
                status='pending',
            )

        payment.purchase_token = purchase_token
        payment.product_id = product_id
        payment.status = 'paid'
        payment.save(update_fields=['purchase_token', 'product_id', 'status'])

        # 5. Deduct / credit user. Since Cafe Bazar already took real money, 
        #    we simply record it and grant the plan entitlement (add to balance first if needed)
        #    The user pays Cafe Bazar directly → backend grants the plan.
        
        traffic_bytes = plan.traffic_bytes_gb * 1024 * 1024 * 1024
        username = generate_username()
        password = generate_password()

        subscription = Subscription.objects.create(
            uuid=uuid.uuid4(),
            user=user,
            plan=plan,
            title=plan.name,
            status='pending',
            username=username,
            password=password,
            total_traffic_byte=traffic_bytes,
            expired_at=timezone.now() + timedelta(days=plan.duration_days),
        )

        resp = anti_sanction_client.add_user(username, password, traffic_bytes)
        if not resp:
            subscription.delete()
            raise ValidationError("Payment verified but unable to activate Anti Sanction. Please contact support.")

        subscription.status = 'active'
        subscription.activated_at = timezone.now()
        subscription.save(update_fields=['status', 'activated_at'])

        payment.status = 'verified'
        payment.transaction_ref = subscription.uuid.hex
        payment.verified_at = timezone.now()
        payment.save(update_fields=['status', 'transaction_ref', 'verified_at'])

        return Response({
            'payment': PaymentSerializer(payment, context=self.get_serializer_context()).data,
            'subscription': __import__(
                'apps.subscriptions.serializers', fromlist=['SubscriptionSerializer']
            ).SubscriptionSerializer(subscription).data,
        }, status=status.HTTP_200_OK)


class PaymentHistoryView(generics.ListAPIView):
    permission_classes = (permissions.IsAuthenticated,)
    serializer_class = PaymentSerializer

    def get_queryset(self):
        return Payment.objects.filter(user=self.request.user).select_related('plan')