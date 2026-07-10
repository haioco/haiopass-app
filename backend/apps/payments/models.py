from django.db import models
from django.contrib.auth.models import User
from apps.plans.models import Plan


class Payment(models.Model):
    STATUS_CHOICES = (
        ('pending', 'Pending'),
        ('paid', 'Paid'),
        ('verified', 'Verified'),
        ('failed', 'Failed'),
        ('refunded', 'Refunded'),
    )

    GATEWAY_CHOICES = (
        ('cafebazar', 'Cafe Bazar'),
    )

    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='payments')
    plan = models.ForeignKey(Plan, on_delete=models.PROTECT, related_name='payments')
    amount_toman = models.PositiveIntegerField(help_text='Amount in Toman')
    gateway = models.CharField(max_length=20, choices=GATEWAY_CHOICES, default='cafebazar')
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default='pending')
    purchase_token = models.CharField(max_length=512, blank=True, default='', help_text='Cafe Bazar purchase token')
    product_id = models.CharField(max_length=200, blank=True, default='', help_text='Cafe Bazar product SKU')
    transaction_ref = models.CharField(max_length=200, blank=True, default='')
    error_message = models.TextField(blank=True, default='')
    verified_at = models.DateTimeField(null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ('-created_at',)
        indexes = (
            models.Index(fields=('user', 'status')),
            models.Index(fields=('purchase_token',)),
        )

    def __str__(self):
        return f"Payment #{self.id} — {self.amount_toman} Toman ({self.status})"