from rest_framework import serializers
from apps.plans.models import Plan
from .models import Subscription


class SubscriptionSerializer(serializers.ModelSerializer):
    plan_detail = serializers.SerializerMethodField(read_only=True)
    plan = serializers.SlugRelatedField(
        slug_field='slug',
        queryset=Plan.objects.filter(is_active=True),
        write_only=True,
        help_text='Plan slug to subscribe to'
    )
    trojan_config_url = serializers.CharField(read_only=True)
    traffic_usage_percent = serializers.FloatField(read_only=True)
    traffic_total_mb = serializers.FloatField(read_only=True)
    traffic_usage_mb = serializers.FloatField(read_only=True)

    class Meta:
        model = Subscription
        fields = (
            'id', 'uuid', 'plan_detail', 'plan', 'status', 'title',
            'username', 'password', 'device_id',
            'traffic_usage_percent', 'traffic_total_mb', 'traffic_usage_mb',
            'total_traffic_byte', 'total_traffic_usage_byte',
            'traffic_is_over', 'expired_at', 'activated_at',
            'created_at', 'trojan_config_url',
        )
        read_only_fields = (
            'id', 'uuid', 'plan_detail', 'status', 'username', 'password',
            'device_id', 'traffic_usage_percent', 'traffic_total_mb',
            'traffic_usage_mb', 'total_traffic_byte',
            'total_traffic_usage_byte', 'traffic_is_over',
            'expired_at', 'activated_at', 'created_at', 'trojan_config_url',
        )

    def get_plan_detail(self, obj):
        from apps.plans.serializers import PlanSerializer
        return PlanSerializer(obj.plan).data

    def validate_title(self, value):
        if len(value) < 2 or len(value) > 30:
            raise serializers.ValidationError("Title must be between 2 and 30 characters.")
        return value

    def validate_plan_slug(self, value):
        if not value.is_active:
            raise serializers.ValidationError("Selected plan is not available.")
        return value


class SubscriptionActivateSerializer(serializers.Serializer):
    device_id = serializers.CharField(max_length=64, required=False, help_text='Android device unique ID')


class CreateSubscriptionAsyncSerializer(serializers.Serializer):
    plan = serializers.SlugRelatedField(
        slug_field='slug',
        queryset=Plan.objects.filter(is_active=True),
        help_text='Plan slug to subscribe to'
    )
    title = serializers.CharField(max_length=30, min_length=2)
    device_id = serializers.CharField(max_length=64, required=False, default='')