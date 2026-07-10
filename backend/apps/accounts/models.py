from django.db import models
from django.contrib.auth.models import User
from django.db.models.signals import post_save
from django.dispatch import receiver


class UserProfile(models.Model):
    user = models.OneToOneField(User, on_delete=models.CASCADE, related_name='profile')
    balance = models.BigIntegerField(default=0, help_text='Balance in Toman')
    mobile = models.CharField(max_length=15, blank=True, null=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    def __str__(self):
        return f"{self.user.username} — {self.balance} Toman"

    def deposit(self, amount_toman, description=None):
        self.balance += amount_toman
        self.save(update_fields=['balance', 'updated_at'])
        return True

    def withdraw(self, amount_toman):
        if self.balance < amount_toman:
            return False
        self.balance -= amount_toman
        self.save(update_fields=['balance', 'updated_at'])
        return True


@receiver(post_save, sender=User)
def create_user_profile(sender, instance, created, **kwargs):
    if created:
        UserProfile.objects.get_or_create(user=instance)