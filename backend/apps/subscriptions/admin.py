from django.contrib import admin, messages
from .models import Subscription
from .tasks import sync_traffic_usage, activate_subscription, renew_subscription


@admin.register(Subscription)
class SubscriptionAdmin(admin.ModelAdmin):
    list_display = ('id', 'uuid', 'user', 'plan', 'status', 'username', 'device_id', 'expired_at')
    search_fields = ('user__username', 'user__email', 'username', 'title', 'device_id')
    list_filter = ('status', 'plan', 'created_at')
    readonly_fields = ('uuid', 'username', 'password', 'created_at', 'updated_at')
    actions = (
        'activate_via_celery',
        'renew_via_celery',
        'sync_traffic_now',
    )
    fieldsets = (
        ('User & Plan', {'fields': ('user', 'plan', 'title', 'uuid', 'device_id')}),
        ('Credentials', {'fields': ('username', 'password')}),
        ('Status & Quota', {'fields': ('status', 'total_traffic_byte', 'total_traffic_usage_byte', 'traffic_is_over')}),
        ('Dates', {'fields': ('expired_at', 'activated_at', 'created_at', 'updated_at')}),
    )

    @admin.action(description='Activate subscription (async via Celery)')
    def activate_via_celery(self, request, queryset):
        pending = queryset.filter(status='pending')
        count = 0
        for sub in pending:
            activate_subscription.delay(sub.id)
            count += 1
        if count:
            self.message_user(request, f'{count} activation task(s) enqueued.', messages.SUCCESS)
        else:
            self.message_user(request, 'No pending subscriptions selected.', messages.WARNING)

    @admin.action(description='Renew subscription (via Celery)')
    def renew_via_celery(self, request, queryset):
        active = queryset.filter(status__in=('active', 'traffic_exhausted', 'expired'))
        count = 0
        for sub in active:
            renew_subscription.delay(sub.id)
            count += 1
        if count:
            self.message_user(request, f'{count} renew task(s) enqueued.', messages.SUCCESS)
        else:
            self.message_user(request, 'No renewable subscriptions selected.', messages.WARNING)

    @admin.action(description='Sync traffic usage now')
    def sync_traffic_now(self, request, queryset):
        result = sync_traffic_usage()
        self.message_user(
            request,
            f"Sync done: {result['updated']} updated, {result['exhausted']} exhausted, {result['failed']} failed",
            messages.INFO,
        )
