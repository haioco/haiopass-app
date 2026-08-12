from rest_framework import generics, permissions, status
from rest_framework.exceptions import ValidationError
from rest_framework.response import Response

from .models import Payment
from .serializers import PaymentSerializer, PaymentVerifySerializer


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
    """DISABLED: Cafe Bazar purchase verification."""
    permission_classes = (permissions.IsAuthenticated,)
    serializer_class = PaymentVerifySerializer

    def post(self, request):
        return Response(
            {'detail': 'Cafe Bazar payment is disabled. Use balance-based purchase via subscriptions/create/.'},
            status=status.HTTP_410_GONE,
        )


class OAuthAuthorizeView(generics.GenericAPIView):
    """DISABLED: Cafe Bazar OAuth."""
    permission_classes = (permissions.AllowAny,)

    def get(self, request):
        return Response(
            {'detail': 'Cafe Bazar is disabled.'},
            status=status.HTTP_410_GONE,
        )


class OAuthCallbackView(generics.GenericAPIView):
    """DISABLED: Cafe Bazar OAuth callback."""
    permission_classes = (permissions.AllowAny,)

    def get(self, request):
        return Response(
            {'detail': 'Cafe Bazar is disabled.'},
            status=status.HTTP_410_GONE,
        )


class PaymentHistoryView(generics.ListAPIView):
    permission_classes = (permissions.IsAuthenticated,)
    serializer_class = PaymentSerializer

    def get_queryset(self):
        return Payment.objects.filter(user=self.request.user).select_related('plan')