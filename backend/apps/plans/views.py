from rest_framework import generics, permissions
from .models import Plan
from .serializers import PlanSerializer


class PlanListView(generics.ListAPIView):
    permission_classes = (permissions.AllowAny,)
    queryset = Plan.objects.filter(is_active=True, is_visible=True)
    serializer_class = PlanSerializer


class PlanDetailView(generics.RetrieveAPIView):
    permission_classes = (permissions.AllowAny,)
    queryset = Plan.objects.filter(is_active=True)
    serializer_class = PlanSerializer
    lookup_field = 'slug'