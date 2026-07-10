from django.urls import path
from .views import PlanListView, PlanDetailView

urlpatterns = [
    path('plans/', PlanListView.as_view(), name='plan-list'),
    path('plans/<slug:slug>/', PlanDetailView.as_view(), name='plan-detail'),
]