from django.db import models


class Plan(models.Model):
    name = models.CharField(max_length=200)
    slug = models.SlugField(max_length=100, unique=True)
    description = models.TextField(blank=True, default='')
    traffic_bytes = models.BigIntegerField(help_text='Total traffic quota in bytes')
    price_toman = models.PositiveIntegerField(help_text='Price in Toman')
    duration_days = models.PositiveIntegerField(default=30, help_text='Subscription duration in days')
    is_active = models.BooleanField(default=True)
    is_visible = models.BooleanField(default=True, help_text='Show in plan listing')
    features = models.JSONField(default=dict, blank=True, help_text='Key-value feature list')
    sort_order = models.PositiveSmallIntegerField(default=0)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ('sort_order', 'price_toman')

    def __str__(self):
        mb = max(1, self.traffic_bytes // (1024 * 1024))
        gb = self.traffic_bytes // (1024 ** 3)
        if gb > 0:
            return f"{self.name} ({gb}GB — {self.price_toman} Toman)"
        return f"{self.name} ({mb}MB — {self.price_toman} Toman)"

    @property
    def traffic_gb(self):
        return round(self.traffic_bytes / (1024 ** 3), 2)

    @property
    def traffic_mb(self):
        return round(self.traffic_bytes / (1024 ** 2), 2)