# import logging
# from datetime import timedelta
# 
# from django.conf import settings
# from django.utils import timezone
# 
# import requests
# 
# logger = logging.getLogger(__name__)
# 
# BAZAAR_AUTH_URL = "https://pardakht.cafebazaar.ir/devapi/v2/auth/token/"
# BAZAAR_AUTHORIZE_URL = "https://pardakht.cafebazaar.ir/devapi/v2/auth/authorize/"
# 
# 
# class CafeBazarClient:
#     def __init__(self):
#         self.base_url = getattr(settings, 'CAFE_BAZAR_API_URL', 'https://pardakht.cafebazaar.ir/devapi/v2/api')
#         self.client_id = settings.CAFE_BAZAR_CLIENT_ID
#         self.client_secret = settings.CAFE_BAZAR_CLIENT_SECRET
#         self.package_name = settings.CAFE_BAZAR_PACKAGE_NAME
#         self.redirect_uri = getattr(settings, 'CAFE_BAZAR_REDIRECT_URI', '')
#         self._access_token = None
# 
#     def get_authorize_url(self):
#         return (
#             f"{BAZAAR_AUTHORIZE_URL}"
#             f"?client_id={self.client_id}"
#             f"&redirect_uri={self.redirect_uri}"
#             f"&response_type=code"
#         )
# 
#     def exchange_code(self, code):
#         from apps.payments.models import OAuthToken
# 
#         try:
#             resp = requests.post(BAZAAR_AUTH_URL, data={
#                 'grant_type': 'authorization_code',
#                 'client_id': self.client_id,
#                 'client_secret': self.client_secret,
#                 'code': code,
#                 'redirect_uri': self.redirect_uri,
#             }, timeout=30)
#             resp.raise_for_status()
#             data = resp.json()
#             access_token = data['access_token']
#             refresh_token = data['refresh_token']
#             expires_in = data.get('expires_in', 3600)
# 
#             OAuthToken.objects.create(
#                 access_token=access_token,
#                 refresh_token=refresh_token,
#                 expires_at=timezone.now() + timedelta(seconds=expires_in),
#             )
#             self._access_token = access_token
#             logger.info("Cafe Bazar OAuth: code exchanged successfully")
#             return access_token
#         except requests.RequestException as e:
#             logger.error("Cafe Bazar exchange_code failed: %s", e)
#             try:
#                 logger.error("Cafe Bazar exchange_code response: %s", resp.text[:500])
#             except Exception:
#                 pass
#             return None
# 
#     def _refresh_access_token(self, oauth_token):
#         from apps.payments.models import OAuthToken
# 
#         try:
#             resp = requests.post(BAZAAR_AUTH_URL, data={
#                 'grant_type': 'refresh_token',
#                 'client_id': self.client_id,
#                 'client_secret': self.client_secret,
#                 'refresh_token': oauth_token.refresh_token,
#             }, timeout=30)
#             resp.raise_for_status()
#             data = resp.json()
#             access_token = data['access_token']
#             refresh_token = data.get('refresh_token', oauth_token.refresh_token)
#             expires_in = data.get('expires_in', 3600)
# 
#             OAuthToken.objects.create(
#                 access_token=access_token,
#                 refresh_token=refresh_token,
#                 expires_at=timezone.now() + timedelta(seconds=expires_in),
#             )
#             self._access_token = access_token
#             logger.info("Cafe Bazar OAuth: access token refreshed")
#             return access_token
#         except requests.RequestException as e:
#             logger.error("Cafe Bazar refresh_token failed: %s", e)
#             return None
# 
#     def _get_access_token(self):
#         if self._access_token:
#             return self._access_token
# 
#         from apps.payments.models import OAuthToken
# 
#         oauth = OAuthToken.get_latest()
#         if not oauth:
#             logger.warning("Cafe Bazar OAuth: no token stored. Visit /api/v1/payments/oauth/authorize/ to authorize.")
#             return None
# 
#         if oauth.expires_at and oauth.expires_at > timezone.now() + timedelta(minutes=5):
#             self._access_token = oauth.access_token
#             return self._access_token
# 
#         return self._refresh_access_token(oauth)
# 
#     def verify_purchase(self, package_name, product_id, purchase_token):
#         access_token = self._get_access_token()
#         if not access_token:
#             return None
# 
#         url = f"{self.base_url}/validate/{package_name}/{product_id}/{purchase_token}/"
#         try:
#             resp = requests.get(url, headers={
#                 'Authorization': f'Bearer {access_token}',
#             }, timeout=30)
#             if resp.status_code == 200:
#                 data = resp.json()
#                 logger.info("Cafe Bazar purchase verified: %s", data)
#                 return data
#             logger.error("Cafe Bazar verify failed [%s]: %s", resp.status_code, resp.text[:300])
#         except requests.RequestException as e:
#             logger.error("Cafe Bazar verify request failed: %s", e)
#         return None
# 
# 
# cafe_bazar_client = CafeBazarClient()

import logging

logger = logging.getLogger(__name__)


class CafeBazarClient:
    """Stub — Cafe Bazar integration is DISABLED."""
    def get_authorize_url(self):
        return None

    def exchange_code(self, code):
        return None

    def verify_purchase(self, package_name, product_id, purchase_token):
        return None


cafe_bazar_client = CafeBazarClient()
