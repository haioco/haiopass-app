from django.contrib import admin
from .models import Subscription


@admin.register(Subscription)
class SubscriptionAdmin(admin.ModelAdmin):
    list_display = ('id', 'uuid', 'user', 'plan', 'status', 'username', 'device_id', 'expired_at')
    search_fields = ('user__username', 'user__email', 'username', 'title', 'device_id')
    list_filter = ('status', 'plan', 'created_at')
    readonly_fields = ('uuid', 'username', 'password', 'created_at', 'updated_at')
    fieldsets = (
        ('User & Plan', {'fields': ('user', 'plan', 'title', 'uuid', 'device_id')}),
        ('Credentials', {'fields': ('username', 'password')}),
        ('Status & Quota', {'fields': ('status', 'total_traffic_byte', 'total_traffic_usage_byte', 'traffic_is_over')}),
        ('Dates', {'fields': ('expired_at', 'activated_at', 'created_at', 'updated_at')}),
    )