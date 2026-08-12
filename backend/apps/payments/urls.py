from django.urls import path
from .views import PaymentCreateView, PaymentVerifyView, PaymentHistoryView

urlpatterns = [
    path('payments/', PaymentCreateView.as_view(), name='payment-create'),
    path('payments/verify/', PaymentVerifyView.as_view(), name='payment-verify'),
    path('payments/history/', PaymentHistoryView.as_view(), name='payment-history'),
]

