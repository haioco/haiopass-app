from django.urls import path
from rest_framework_simplejwt.views import TokenRefreshView, TokenVerifyView

from .views import RegisterView, MeView, BalanceView
from .views import TokenObtainPairView

urlpatterns = [
    path('auth/register/', RegisterView.as_view(), name='auth-register'),
    path('auth/login/', TokenObtainPairView.as_view(), name='auth-login'),
    path('auth/token/refresh/', TokenRefreshView.as_view(), name='auth-refresh'),
    path('auth/token/verify/', TokenVerifyView.as_view(), name='auth-verify'),
    path('user/me/', MeView.as_view(), name='user-me'),
    path('user/balance/', BalanceView.as_view(), name='user-balance'),
]