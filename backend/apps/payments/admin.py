from django.contrib import admin
from .models import Payment  # OAuthToken commented out — Cafe Bazar DISABLED
# from .services.cafe_bazar import cafe_bazar_client  # DISABLED


# @admin.register(OAuthToken)  # DISABLED
# class OAuthTokenAdmin(admin.ModelAdmin):
#     list_display = ('id', 'expires_at', 'created_at', 'updated_at')
#     readonly_fields = ('access_token', 'refresh_token', 'expires_at', 'created_at', 'updated_at')
#     actions = ['trigger_reauthorize']
# 
#     def trigger_reauthorize(self, request, queryset):
#         messages.info(request, f'Go to: {cafe_bazar_client.get_authorize_url()}')
#     trigger_reauthorize.short_description = 'Show authorize URL to re-authorize'


@admin.register(Payment)
class PaymentAdmin(admin.ModelAdmin):
    list_display = ('id', 'user', 'plan', 'amount_toman', 'gateway', 'status', 'verified_at', 'created_at')
    search_fields = ('user__username', 'purchase_token', 'product_id')
    list_filter = ('gateway', 'status', 'created_at')
    readonly_fields = ('purchase_token', 'verified_at', 'created_at', 'updated_at')
    fieldsets = (
        ('Payment Info', {'fields': ('user', 'plan', 'amount_toman', 'gateway', 'status')}),
        ('Purchase', {'fields': ('purchase_token', 'product_id')}),
        ('Result', {'fields': ('transaction_ref', 'error_message', 'verified_at')}),
        ('Timestamps', {'fields': ('created_at', 'updated_at')}),
    )
