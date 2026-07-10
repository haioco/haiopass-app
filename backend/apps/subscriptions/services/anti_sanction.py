import logging
import string
import random
from django.conf import settings

import requests

logger = logging.getLogger(__name__)


class AntiSanctionClient:
    def __init__(self):
        self.base_url = settings.TAHRIM_API_BASE_URL
        self.auth_token = settings.TAHRIM_AUTH_TOKEN
        self.timeout = 60

    def _request(self, method, path, json_data=None, params=None, max_retries=3):
        url = f"{self.base_url}{path}"
        headers = {
            'Content-Type': 'application/json',
            'Authorization': self.auth_token,
        }

        for attempt in range(1, max_retries + 1):
            try:
                resp = requests.request(
                    method, url,
                    json=json_data,
                    params=params,
                    headers=headers,
                    timeout=self.timeout,
                    verify=False,
                )
                if resp.status_code in (200, 201):
                    return resp.json()
                logger.warning(
                    "AntiSanction API returned %s on attempt %d/%d: %s",
                    resp.status_code, attempt, max_retries, resp.text[:300]
                )
            except requests.RequestException as e:
                logger.error("AntiSanction API request failed attempt %d/%d: %s", attempt, max_retries, e)

            if attempt < max_retries:
                import time
                time.sleep(random.randint(1, 3))

        return None

    def add_user(self, username, password, quota_bytes):
        resp = self._request('POST', '/add_user', json_data={
            'username': username,
            'password': password,
            'quota': quota_bytes,
        })
        if resp and 'added successfully' in str(resp.get('message', '')):
            return resp
        return None

    def remove_user(self, username):
        resp = self._request('DELETE', '/remove_user', params={'username': username})
        if resp and 'removed successfully' in str(resp.get('message', '')):
            return resp
        return None

    def get_quota(self, username):
        resp = self._request('GET', '/get_user_total_quota', params={'username': username})
        if resp and resp.get('username') and resp.get('total_quota') is not None:
            return resp
        return None

    def renew_user(self, username, password, quota_bytes):
        self.remove_user(username)
        import time
        time.sleep(1)
        return self.add_user(username, password, quota_bytes)


anti_sanction_client = AntiSanctionClient()


def generate_username():
    chars = string.ascii_lowercase + string.digits
    while True:
        username = ''.join(random.choices(chars, k=10))
        from apps.subscriptions.models import Subscription
        if not Subscription.objects.filter(username=username).exists():
            return username


def generate_password():
    chars = string.ascii_lowercase + string.digits
    return ''.join(random.choices(chars, k=12))


def bytes_to_gb(total_bytes):
    return round(total_bytes / (1024 ** 3), 2)