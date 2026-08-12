import environ

env = environ.Env(
    DEBUG=(bool, False),
    ALLOWED_HOSTS=(list, ['127.0.0.1', 'localhost']),
    SECRET_KEY=(str, ''),
    DATABASE_URL=(str, 'sqlite:///db.sqlite3'),
    REDIS_URL=(str, 'redis://localhost:6379/0'),
    CELERY_BROKER_URL=(str, 'redis://localhost:6379/1'),
    TAHRIM_API_BASE_URL=(str, 'http://tahrim.haiocloud.com:5000'),
    TAHRIM_AUTH_TOKEN=(str, 'Bearer access_token_1'),
    TAHRIM_DOMAIN=(str, 'tahrim.haiocloud.com'),
    # CAFE_BAZAR_CLIENT_ID=(str, ''),
    # CAFE_BAZAR_CLIENT_SECRET=(str, ''),
    # CAFE_BAZAR_PACKAGE_NAME=(str, 'com.haio.bypass'),
    # CAFE_BAZAR_API_URL=(str, 'https://pardakht.cafebazaar.ir/devapi/v2/api'),
    # CAFE_BAZAR_REDIRECT_URI=(str, ''),
    JWT_ACCESS_TOKEN_LIFETIME_MINUTES=(int, 120),
    JWT_REFRESH_TOKEN_LIFETIME_DAYS=(int, 7),
    CSRF_TRUSTED_ORIGINS=(list, ['https://haiobypass.haiocloud.com']),
    SECURE_SSL_REDIRECT=(bool, True),
    SESSION_COOKIE_SECURE=(bool, True),
    CSRF_COOKIE_SECURE=(bool, True),
)

BASE_DIR = environ.Path(__file__) - 2

environ.Env.read_env(BASE_DIR('.env'))

SECRET_KEY = env('SECRET_KEY')
DEBUG = env('DEBUG')
ALLOWED_HOSTS = env('ALLOWED_HOSTS')

CSRF_TRUSTED_ORIGINS = env('CSRF_TRUSTED_ORIGINS')
SECURE_SSL_REDIRECT = env('SECURE_SSL_REDIRECT')
SESSION_COOKIE_SECURE = env('SESSION_COOKIE_SECURE')
CSRF_COOKIE_SECURE = env('CSRF_COOKIE_SECURE')
SECURE_PROXY_SSL_HEADER = ("HTTP_X_FORWARDED_PROTO", "https")

INSTALLED_APPS = [
    'django.contrib.admin',
    'django.contrib.auth',
    'django.contrib.contenttypes',
    'django.contrib.sessions',
    'django.contrib.messages',
    'django.contrib.staticfiles',

    'rest_framework',
    'rest_framework_simplejwt',
    'corsheaders',
    'django_filters',
    'drf_spectacular',

    'django_celery_results',
    'django_celery_beat',

    'apps.accounts',
    'apps.plans',
    'apps.subscriptions',
    'apps.payments',
]

MIDDLEWARE = [
    'django.middleware.security.SecurityMiddleware',
    'whitenoise.middleware.WhiteNoiseMiddleware',
    'django.contrib.sessions.middleware.SessionMiddleware',
    'corsheaders.middleware.CorsMiddleware',
    'django.middleware.common.CommonMiddleware',
    'django.middleware.csrf.CsrfViewMiddleware',
    'django.contrib.auth.middleware.AuthenticationMiddleware',
    'django.contrib.messages.middleware.MessageMiddleware',
    'django.middleware.clickjacking.XFrameOptionsMiddleware',
]

ROOT_URLCONF = 'config.urls'

TEMPLATES = [
    {
        'BACKEND': 'django.template.backends.django.DjangoTemplates',
        'DIRS': [],
        'APP_DIRS': True,
        'OPTIONS': {
            'context_processors': [
                'django.template.context_processors.debug',
                'django.template.context_processors.request',
                'django.contrib.auth.context_processors.auth',
                'django.contrib.messages.context_processors.messages',
            ],
        },
    },
]

WSGI_APPLICATION = 'config.wsgi.application'

DATABASES = {
    'default': env.db_url_config(env('DATABASE_URL')),
}

AUTH_PASSWORD_VALIDATORS = [
    {'NAME': 'django.contrib.auth.password_validation.MinimumLengthValidator', 'OPTIONS': {'min_length': 6}},
]

LANGUAGE_CODE = 'en-us'
TIME_ZONE = 'Asia/Tehran'
USE_I18N = True
USE_TZ = True

STATIC_URL = 'static/'
STATIC_ROOT = BASE_DIR('staticfiles')
STATICFILES_STORAGE = 'whitenoise.storage.CompressedManifestStaticFilesStorage'

MEDIA_URL = 'media/'
MEDIA_ROOT = BASE_DIR('media')

DEFAULT_AUTO_FIELD = 'django.db.models.BigAutoField'

REST_FRAMEWORK = {
    'DEFAULT_AUTHENTICATION_CLASSES': (
        'rest_framework_simplejwt.authentication.JWTAuthentication',
    ),
    'DEFAULT_PERMISSION_CLASSES': (
        'rest_framework.permissions.IsAuthenticated',
    ),
    'DEFAULT_FILTER_BACKENDS': (
        'django_filters.rest_framework.DjangoFilterBackend',
    ),
    'DEFAULT_PAGINATION_CLASS': 'rest_framework.pagination.PageNumberPagination',
    'PAGE_SIZE': 20,
    'DEFAULT_SCHEMA_CLASS': 'drf_spectacular.openapi.AutoSchema',
}

SPECTACULAR_SETTINGS = {
    'TITLE': 'HaioBypass API',
    'DESCRIPTION': 'Backend for HaioBypass Android app — plan purchase, anti-sanction activation',
    'VERSION': '1.0.0',
    'SERVE_INCLUDE_SCHEMA': False,
    'COMPONENT_SPLIT_REQUEST': True,
}

from datetime import timedelta

SIMPLE_JWT = {
    'ACCESS_TOKEN_LIFETIME': timedelta(minutes=env('JWT_ACCESS_TOKEN_LIFETIME_MINUTES')),
    'REFRESH_TOKEN_LIFETIME': timedelta(days=env('JWT_REFRESH_TOKEN_LIFETIME_DAYS')),
    'ROTATE_REFRESH_TOKENS': True,
    'BLACKLIST_AFTER_ROTATION': True,
    'AUTH_HEADER_TYPES': ('Bearer',),
}

CORS_ALLOW_ALL_ORIGINS = True

# AntiSanction Tahrim API
TAHRIM_API_BASE_URL = env('TAHRIM_API_BASE_URL')
TAHRIM_AUTH_TOKEN = env('TAHRIM_AUTH_TOKEN')
TAHRIM_DOMAIN = env('TAHRIM_DOMAIN')

# Cafe Bazar Payment (DISABLED — kept for reference)
# CAFE_BAZAR_CLIENT_ID = env('CAFE_BAZAR_CLIENT_ID')
# CAFE_BAZAR_CLIENT_SECRET = env('CAFE_BAZAR_CLIENT_SECRET')
# CAFE_BAZAR_PACKAGE_NAME = env('CAFE_BAZAR_PACKAGE_NAME')
# CAFE_BAZAR_API_URL = env('CAFE_BAZAR_API_URL')
# CAFE_BAZAR_REDIRECT_URI = env('CAFE_BAZAR_REDIRECT_URI')

# Celery
CELERY_BROKER_URL = env('CELERY_BROKER_URL')
CELERY_RESULT_BACKEND = 'django-db'
CELERY_RESULT_EXTENDED = True
CELERY_ACCEPT_CONTENT = ['json']
CELERY_TASK_SERIALIZER = 'json'
CELERY_RESULT_SERIALIZER = 'json'
CELERY_TIMEZONE = TIME_ZONE
CELERY_BEAT_SCHEDULER = 'django_celery_beat.schedulers:DatabaseScheduler'