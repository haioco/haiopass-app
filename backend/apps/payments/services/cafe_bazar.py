import logging
from django.conf import settings

import requests

logger = logging.getLogger(__name__)

# Note: Cafe Bazar's actual verification API endpoint depends on their REST API v2 docs.
# The documented flow is:
# 1. Android app calls CafeBazaarIabHelper.launchPurchaseFlow() with product SKU
# 2. On success, app receives purchase token from Cafe Bazar SDK
# 3. App sends token to backend POST /api/v1/payments/verify/
# 4. Backend verifies token with Cafe Bazar REST API
#    URL: https://pardakht.cafebazaar.ir/devapi/v2/api/validate/{packageName}/{productId}/{token}/
# 5. If valid, backend grants entitlement

# Reference: https://developers.cafebazaar.ir/fa/docs/payment/iab-rest-api/

BAZAAR_AUTH_URL = "https://pardakht.cafebazaar.ir/devapi/v2/auth/token/"


class CafeBazarClient:
    def __init__(self):
        self.base_url = getattr(settings, 'CAFE_BAZAR_API_URL', 'https://pardakht.cafebazaar.ir/devapi/v2/api')
        self.client_id = settings.CAFE_BAZAR_CLIENT_ID
        self.client_secret = settings.CAFE_BAZAR_CLIENT_SECRET
        self.package_name = settings.CAFE_BAZAR_PACKAGE_NAME
        self._access_token = None

    def _get_access_token(self):
        if self._access_token:
            return self._access_token

        try:
            resp = requests.post(BAZAAR_AUTH_URL, json={
                'client_id': self.client_id,
                'client_secret': self.client_secret,
                'grant_type': 'client_credentials',
            }, timeout=30)
            if resp.status_code == 200:
                data = resp.json()
                self._access_token = data.get('access_token')
                return self._access_token
            logger.error("Cafe Bazar auth failed: %s %s", resp.status_code, resp.text[:300])
        except requests.RequestException as e:
            logger.error("Cafe Bazar auth request failed: %s", e)
        return None

    def verify_purchase(self, package_name, product_id, purchase_token):
        """
        Verify a Cafe Bazar in-app purchase token.

        Returns dict with keys: kind, developerPayload, purchaseTime, purchaseState,
        consumptionState, developerPayload.
        """
        access_token = self._get_access_token()
        if not access_token:
            return None

        url = f"{self.base_url}/validate/{package_name}/{product_id}/{purchase_token}/"
        try:
            resp = requests.get(url, headers={
                'Authorization': f'Bearer {access_token}',
            }, timeout=30)
            if resp.status_code == 200:
                data = resp.json()
                logger.info("Cafe Bazar purchase verified: %s", data)
                return data
            logger.error("Cafe Bazar verify failed [%s]: %s", resp.status_code, resp.text[:300])
        except requests.RequestException as e:
            logger.error("Cafe Bazar verify request failed: %s", e)
        return None


cafe_bazar_client = CafeBazarClient()