from django.contrib import admin
from .models import Plan


@admin.register(Plan)
class PlanAdmin(admin.ModelAdmin):
    list_display = ('name', 'slug', 'traffic_bytes', 'price_toman', 'duration_days', 'is_active', 'is_visible', 'sort_order')
    list_editable = ('is_active', 'is_visible', 'sort_order')
    search_fields = ('name', 'slug')
    prepopulated_fields = {'slug': ('name',)}
    list_filter = ('is_active', 'is_visible')
    readonly_fields = ('created_at', 'updated_at')
    fieldsets = (
        ('Basic Info', {'fields': ('name', 'slug', 'description')}),
        ('Pricing & Quota', {'fields': ('traffic_bytes', 'price_toman', 'duration_days')}),
        # ('Cafe Bazaar', {'fields': ('bazaar_sku',)}),
        ('Display', {'fields': ('is_active', 'is_visible', 'features', 'sort_order')}),
        ('Timestamps', {'fields': ('created_at', 'updated_at')}),
    )