from rest_framework import serializers
from apps.plans.models import Plan
from .models import Payment


class PaymentSerializer(serializers.ModelSerializer):
    plan_detail = serializers.SerializerMethodField(read_only=True)
    plan = serializers.SlugRelatedField(
        slug_field='slug',
        queryset=Plan.objects.filter(is_active=True),
        write_only=True,
        help_text='Plan slug being purchased'
    )

    class Meta:
        model = Payment
        fields = (
            'id', 'plan_detail', 'plan', 'amount_toman', 'gateway', 'status',
            'purchase_token', 'product_id', 'transaction_ref',
            'verified_at', 'created_at',
        )
        read_only_fields = (
            'id', 'plan_detail', 'amount_toman', 'gateway', 'status',
            'transaction_ref', 'verified_at', 'created_at',
        )

    def get_plan_detail(self, obj):
        from apps.plans.serializers import PlanSerializer
        return PlanSerializer(obj.plan).data


class PaymentVerifySerializer(serializers.Serializer):
    purchase_token = serializers.CharField(max_length=512)
    product_id = serializers.CharField(max_length=200)
    payment_id = serializers.IntegerField(required=False, help_text='Optional existing payment ID')