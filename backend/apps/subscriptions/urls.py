from django.urls import path
from .views import (
    SubscriptionListView, SubscriptionDetailView,
    SubscriptionCreateView, SubscriptionCreateAsyncView,
    SubscriptionAutoActivateView,
    SubscriptionRenewView, SubscriptionDeviceDetailView,
)

urlpatterns = [
    path('subscriptions/', SubscriptionListView.as_view(), name='subscription-list'),
    path('subscriptions/<uuid:uuid>/', SubscriptionDetailView.as_view(), name='subscription-detail'),
    path('subscriptions/create/', SubscriptionCreateView.as_view(), name='subscription-create'),
    path('subscriptions/create-async/', SubscriptionCreateAsyncView.as_view(), name='subscription-create-async'),
    path('subscriptions/auto-activate/', SubscriptionAutoActivateView.as_view(), name='subscription-auto-activate'),
    path('subscriptions/<uuid:uuid>/renew/', SubscriptionRenewView.as_view(), name='subscription-renew'),
    path('subscriptions/<uuid:uuid>/device-status/', SubscriptionDeviceDetailView.as_view(), name='subscription-device-status'),
]