from rest_framework import serializers
from .models import Plan


class PlanSerializer(serializers.ModelSerializer):
    traffic_gb = serializers.FloatField(read_only=True)
    traffic_mb = serializers.FloatField(read_only=True)

    class Meta:
        model = Plan
        fields = (
            'id', 'name', 'slug', 'description',
            'traffic_bytes', 'traffic_gb', 'traffic_mb',
            'price_toman', 'duration_days',
            'features', 'sort_order',
        )
        read_only_fields = ('id', 'slug', 'traffic_gb', 'traffic_mb')