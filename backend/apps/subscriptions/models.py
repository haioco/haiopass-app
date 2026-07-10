import uuid
from datetime import timedelta

from django.db import models
from django.contrib.auth.models import User
from django.utils import timezone
from apps.plans.models import Plan


class Subscription(models.Model):
    STATUS_CHOICES = (
        ('active', 'Active'),
        ('expired', 'Expired'),
        ('traffic_exhausted', 'Traffic Exhausted'),
        ('cancelled', 'Cancelled'),
        ('pending', 'Pending Activation'),
    )

    uuid = models.UUIDField(default=uuid.uuid4, unique=True, editable=False)
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='subscriptions')
    plan = models.ForeignKey(Plan, on_delete=models.PROTECT, related_name='subscriptions')
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default='pending')
    title = models.CharField(max_length=30, help_text='User-given label for this subscription')
    username = models.CharField(max_length=30, unique=True, db_index=True)
    password = models.CharField(max_length=50)
    device_id = models.CharField(max_length=64, blank=True, default='', db_index=True, help_text='Android device unique ID for free plan tracking')
    total_traffic_byte = models.BigIntegerField(help_text='Total quota in bytes')
    total_traffic_usage_byte = models.BigIntegerField(default=0, help_text='Used traffic in bytes')
    traffic_is_over = models.BooleanField(default=False)
    expired_at = models.DateTimeField(default=timezone.now)
    activated_at = models.DateTimeField(null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ('-created_at',)
        indexes = (
            models.Index(fields=('user', 'status')),
            models.Index(fields=('username',)),
            models.Index(fields=('device_id', 'plan')),
        )

    def __str__(self):
        return f"Subscription #{self.id} — {self.plan.name} ({self.user.username})"

    def save(self, *args, **kwargs):
        if self.plan_id:
            self.total_traffic_byte = self.plan.traffic_bytes
            self.expired_at = (self.activated_at or timezone.now()) + timedelta(days=self.plan.duration_days)
        super().save(*args, **kwargs)

    @property
    def traffic_usage_percent(self):
        if self.total_traffic_byte <= 0:
            return 0
        return min(100, round((self.total_traffic_usage_byte / self.total_traffic_byte) * 100, 2))

    @property
    def traffic_total_mb(self):
        return round(self.total_traffic_byte / (1024 ** 2), 2)

    @property
    def traffic_usage_mb(self):
        return round(self.total_traffic_usage_byte / (1024 ** 2), 2)

    @property
    def trojan_config_url(self):
        from django.conf import settings
        domain = getattr(settings, 'TAHRIM_DOMAIN', 'tahrim.haiocloud.com')
        return f"trojan://{self.password}@{domain}:443?allowInsecureCertificate=1&allowInsecure=1&sni={domain}#{self.title}"