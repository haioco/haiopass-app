from django.contrib.auth.models import User
from rest_framework import serializers
from .models import UserProfile


class UserProfileSerializer(serializers.ModelSerializer):
    class Meta:
        model = UserProfile
        fields = ('balance', 'mobile', 'created_at')


class RegisterSerializer(serializers.ModelSerializer):
    password = serializers.CharField(write_only=True, min_length=6)
    mobile = serializers.CharField(write_only=True, required=False, allow_blank=True)

    class Meta:
        model = User
        fields = ('id', 'username', 'email', 'password', 'mobile')

    def create(self, validated_data):
        mobile = validated_data.pop('mobile', None)
        user = User.objects.create_user(
            username=validated_data['username'],
            email=validated_data.get('email', ''),
            password=validated_data['password'],
        )
        if mobile:
            user.profile.mobile = mobile
            user.profile.save(update_fields=['mobile'])
        return user

    def to_representation(self, instance):
        data = super().to_representation(instance)
        data.pop('password', None)
        data['profile'] = UserProfileSerializer(instance.profile).data
        return data


class UserSerializer(serializers.ModelSerializer):
    profile = UserProfileSerializer(read_only=True)
    is_staff = serializers.BooleanField(read_only=True)

    class Meta:
        model = User
        fields = ('id', 'username', 'email', 'profile', 'is_staff', 'date_joined')
        read_only_fields = ('id', 'username', 'date_joined', 'is_staff')