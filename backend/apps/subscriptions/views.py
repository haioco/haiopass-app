import uuid
from datetime import timedelta

from django.db import transaction
from django.utils import timezone
from rest_framework import generics, permissions, status
from rest_framework.exceptions import ValidationError
from rest_framework.response import Response

from .models import Subscription
from .serializers import (
    SubscriptionSerializer,
    SubscriptionActivateSerializer,
    CreateSubscriptionAsyncSerializer,
)
from .services.anti_sanction import (
    anti_sanction_client, generate_username, generate_password
)
from .tasks import activate_subscription
from apps.plans.models import Plan


class SubscriptionListView(generics.ListAPIView):
    permission_classes = (permissions.IsAuthenticated,)
    serializer_class = SubscriptionSerializer

    def get_queryset(self):
        return Subscription.objects.filter(user=self.request.user).select_related('plan')


class SubscriptionDetailView(generics.RetrieveAPIView):
    permission_classes = (permissions.IsAuthenticated,)
    serializer_class = SubscriptionSerializer
    lookup_field = 'uuid'

    def get_queryset(self):
        return Subscription.objects.filter(user=self.request.user).select_related('plan')


class SubscriptionCreateView(generics.CreateAPIView):
    permission_classes = (permissions.IsAuthenticated,)
    serializer_class = SubscriptionSerializer

    @transaction.atomic
    def create(self, request, *args, **kwargs):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        user = request.user
        plan = serializer.validated_data['plan']
        title = serializer.validated_data['title']
        device_id = request.data.get('device_id', '')

        if plan.price_toman == 0:
            self._check_free_plan_eligibility(user, plan, device_id)

        if plan.price_toman > 0 and user.profile.balance < plan.price_toman:
            raise ValidationError(
                f"Insufficient balance. Need {plan.price_toman} Toman, you have {user.profile.balance} Toman."
            )

        username = generate_username()
        password = generate_password()

        subscription = Subscription(
            uuid=uuid.uuid4(),
            user=user,
            plan=plan,
            title=title,
            status='pending',
            username=username,
            password=password,
            device_id=device_id,
        )
        subscription.save()

        resp = anti_sanction_client.add_user(username, password, plan.traffic_bytes)
        if not resp:
            raise ValidationError("Currently unable to activate Anti Sanction. Please try again later.")

        subscription.status = 'active'
        subscription.activated_at = timezone.now()
        subscription.save(update_fields=['status', 'activated_at'])

        if plan.price_toman > 0 and not user.profile.withdraw(plan.price_toman):
            subscription.delete()
            anti_sanction_client.remove_user(username)
            raise ValidationError("Balance deduction failed.")

        return Response(
            SubscriptionSerializer(subscription, context=self.get_serializer_context()).data,
            status=status.HTTP_201_CREATED,
        )

    def _check_free_plan_eligibility(self, user, plan, device_id):
        if device_id:
            existing = Subscription.objects.filter(
                plan__price_toman=0,
                device_id=device_id,
                status__in=('active', 'pending'),
            ).exists()
            if existing:
                raise ValidationError("This device has already used the free plan.")
        else:
            existing = Subscription.objects.filter(
                user=user,
                plan__price_toman=0,
                status__in=('active', 'pending'),
            ).exists()
            if existing:
                raise ValidationError("You have already used the free plan.")


class SubscriptionCreateAsyncView(generics.GenericAPIView):
    """
    Create a subscription asynchronously via Celery.
    The subscription is created with status='pending' and a Celery task
    activates it on the anti-sanction server.
    """
    permission_classes = (permissions.IsAuthenticated,)
    serializer_class = CreateSubscriptionAsyncSerializer

    @transaction.atomic
    def post(self, request, *args, **kwargs):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        user = request.user
        plan = serializer.validated_data['plan']
        title = serializer.validated_data['title']
        device_id = serializer.validated_data.get('device_id', '')

        if plan.price_toman == 0:
            self._check_free_plan_eligibility(user, plan, device_id)

        if plan.price_toman > 0 and user.profile.balance < plan.price_toman:
            raise ValidationError(
                f"Insufficient balance. Need {plan.price_toman} Toman, you have {user.profile.balance} Toman."
            )

        username = generate_username()
        password = generate_password()

        subscription = Subscription(
            uuid=uuid.uuid4(),
            user=user,
            plan=plan,
            title=title,
            status='pending',
            username=username,
            password=password,
            device_id=device_id,
        )
        subscription.save()

        if plan.price_toman > 0:
            if not user.profile.withdraw(plan.price_toman):
                subscription.delete()
                raise ValidationError("Balance deduction failed.")

        activate_subscription.delay(subscription.id)

        return Response(
            SubscriptionSerializer(subscription, context=self.get_serializer_context()).data,
            status=status.HTTP_202_ACCEPTED,
        )

    def _check_free_plan_eligibility(self, user, plan, device_id):
        if device_id:
            existing = Subscription.objects.filter(
                plan__price_toman=0,
                device_id=device_id,
                status__in=('active', 'pending'),
            ).exists()
            if existing:
                raise ValidationError("This device has already used the free plan.")
        else:
            existing = Subscription.objects.filter(
                user=user,
                plan__price_toman=0,
                status__in=('active', 'pending'),
            ).exists()
            if existing:
                raise ValidationError("You have already used the free plan.")


class SubscriptionAutoActivateView(generics.GenericAPIView):
    """
    Auto-activate free plan on first app launch.
    Called by Android app on first open — device_id required.
    Creates user implicitly if needed, activates free 50MB plan, returns trojan config.
    """
    permission_classes = (permissions.AllowAny,)
    serializer_class = SubscriptionActivateSerializer

    @transaction.atomic
    def post(self, request):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        device_id = serializer.validated_data.get('device_id')
        if not device_id:
            raise ValidationError("device_id is required for auto-activation.")

        free_plan = Plan.objects.filter(slug='free', is_active=True).first()
        if not free_plan:
            raise ValidationError("Free plan is not available.")

        existing = Subscription.objects.filter(
            plan__price_toman=0,
            device_id=device_id,
            status__in=('active', 'pending'),
        ).exists()
        if existing:
            raise ValidationError("This device has already activated the free plan.")

        from django.contrib.auth.models import User
        import string, random

        chars = string.ascii_lowercase + string.digits
        while True:
            rand_user = 'd_' + ''.join(random.choices(chars, k=12))
            if not User.objects.filter(username=rand_user).exists():
                break
        user = User.objects.create_user(username=rand_user, password=None)

        username = generate_username()
        password = generate_password()

        subscription = Subscription(
            uuid=uuid.uuid4(),
            user=user,
            plan=free_plan,
            title='Free 50MB',
            status='pending',
            username=username,
            password=password,
            device_id=device_id,
        )
        subscription.save()

        resp = anti_sanction_client.add_user(username, password, free_plan.traffic_bytes)
        if not resp:
            user.delete()
            raise ValidationError("Currently unable to activate Anti Sanction. Please try again later.")

        subscription.status = 'active'
        subscription.activated_at = timezone.now()
        subscription.save(update_fields=['status', 'activated_at'])

        return Response({
            'subscription': SubscriptionSerializer(subscription).data,
            'trojan_config_url': subscription.trojan_config_url,
            'user_id': user.id,
        }, status=status.HTTP_201_CREATED)


class SubscriptionDeviceDetailView(generics.GenericAPIView):
    permission_classes = (permissions.AllowAny,)
    serializer_class = SubscriptionSerializer

    def post(self, request, uuid):
        device_id = request.data.get('device_id', '')
        if not device_id:
            raise ValidationError("device_id is required.")

        try:
            subscription = Subscription.objects.get(uuid=uuid, device_id=device_id, status='active')
        except Subscription.DoesNotExist:
            raise ValidationError("Subscription not found.")

        return Response(SubscriptionSerializer(subscription).data)


class SubscriptionRenewView(generics.GenericAPIView):
    permission_classes = (permissions.IsAuthenticated,)
    serializer_class = SubscriptionSerializer
    lookup_field = 'uuid'

    def get_queryset(self):
        return Subscription.objects.filter(user=self.request.user).select_related('plan')

    @transaction.atomic
    def post(self, request, uuid):
        subscription = self.get_object()
        user = request.user
        plan = subscription.plan

        if plan.price_toman > 0 and user.profile.balance < plan.price_toman:
            raise ValidationError(
                f"Insufficient balance. Need {plan.price_toman} Toman, you have {user.profile.balance} Toman."
            )

        if plan.price_toman > 0 and not user.profile.withdraw(plan.price_toman):
            raise ValidationError("Balance deduction failed.")

        now = timezone.now()
        needs_full_renew = (
            subscription.expired_at <= now
            or subscription.traffic_usage_byte >= subscription.total_traffic_byte
            or subscription.traffic_is_over
        )

        if needs_full_renew:
            subscription.total_traffic_byte = plan.traffic_bytes
            subscription.total_traffic_usage_byte = 0
            subscription.traffic_is_over = False
            subscription.status = 'active'
            subscription.expired_at = now + timedelta(days=plan.duration_days)
            subscription.save(update_fields=[
                'total_traffic_byte', 'total_traffic_usage_byte',
                'traffic_is_over', 'status', 'expired_at', 'updated_at',
            ])
            resp = anti_sanction_client.renew_user(
                subscription.username, subscription.password, plan.traffic_bytes
            )
            if not resp:
                raise ValidationError("Currently unable to renew Anti Sanction. Please try again later.")
        else:
            subscription.expired_at = now + timedelta(days=plan.duration_days)
            subscription.save(update_fields=['expired_at', 'updated_at'])

        return Response(
            SubscriptionSerializer(subscription, context=self.get_serializer_context()).data,
        )