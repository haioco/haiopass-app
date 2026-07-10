from django.contrib import admin
from .models import Payment


@admin.register(Payment)
class PaymentAdmin(admin.ModelAdmin):
    list_display = ('id', 'user', 'plan', 'amount_toman', 'gateway', 'status', 'verified_at', 'created_at')
    search_fields = ('user__username', 'purchase_token', 'product_id')
    list_filter = ('gateway', 'status', 'created_at')
    readonly_fields = ('purchase_token', 'verified_at', 'created_at', 'updated_at')
    fieldsets = (
        ('Payment Info', {'fields': ('user', 'plan', 'amount_toman', 'gateway', 'status')}),
        ('Cafe Bazar', {'fields': ('purchase_token', 'product_id')}),
        ('Result', {'fields': ('transaction_ref', 'error_message', 'verified_at')}),
        ('Timestamps', {'fields': ('created_at', 'updated_at')}),
    )