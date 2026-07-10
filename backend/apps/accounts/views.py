from django.contrib.auth.models import User
from rest_framework import generics, status, permissions
from rest_framework.response import Response
from rest_framework_simplejwt.tokens import RefreshToken
from rest_framework_simplejwt.views import TokenObtainPairView as BaseTokenObtainPairView
from rest_framework_simplejwt.serializers import TokenObtainPairSerializer

from .models import UserProfile
from .serializers import RegisterSerializer, UserSerializer


class CustomTokenObtainPairSerializer(TokenObtainPairSerializer):
    def validate(self, attrs):
        data = super().validate(attrs)
        data['user'] = {
            'id': self.user.id,
            'username': self.user.username,
            'email': self.user.email,
            'balance': self.user.profile.balance,
            'mobile': self.user.profile.mobile,
        }
        return data


class TokenObtainPairView(BaseTokenObtainPairView):
    serializer_class = CustomTokenObtainPairSerializer


class RegisterView(generics.CreateAPIView):
    queryset = User.objects.all()
    permission_classes = (permissions.AllowAny,)
    serializer_class = RegisterSerializer

    def create(self, request, *args, **kwargs):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        user = serializer.save()
        refresh = RefreshToken.for_user(user)
        return Response({
            'user': serializer.data,
            'tokens': {
                'access': str(refresh.access_token),
                'refresh': str(refresh),
            },
        }, status=status.HTTP_201_CREATED)


class MeView(generics.RetrieveAPIView):
    permission_classes = (permissions.IsAuthenticated,)
    serializer_class = UserSerializer

    def get_object(self):
        return self.request.user


class BalanceView(generics.RetrieveAPIView):
    permission_classes = (permissions.IsAuthenticated,)

    def get(self, request):
        profile = request.user.profile
        return Response({
            'balance': profile.balance,
            'mobile': profile.mobile,
        })