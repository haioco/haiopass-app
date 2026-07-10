import logging
from celery import shared_task
from django.db.models import Q
from django.utils import timezone

from .models import Subscription
from .services.anti_sanction import anti_sanction_client

logger = logging.getLogger(__name__)


@shared_task(name='subscriptions.sync_traffic_usage')
def sync_traffic_usage():
    active_subs = Subscription.objects.filter(
        status='active',
    ).select_related('plan').only(
        'id', 'username', 'total_traffic_byte', 'total_traffic_usage_byte',
        'traffic_is_over', 'status', 'plan__traffic_bytes',
    )

    updated = 0
    exhausted = 0
    failed = 0

    for sub in active_subs:
        try:
            resp = anti_sanction_client.get_quota(sub.username)
            if not resp or resp.get('total_quota') is None:
                failed += 1
                continue

            remaining_quota = int(resp['total_quota'])
            used_bytes = sub.total_traffic_byte - remaining_quota
            if used_bytes < 0:
                used_bytes = 0

            save_fields = ['total_traffic_usage_byte', 'updated_at']

            if used_bytes >= sub.total_traffic_byte and not sub.traffic_is_over:
                sub.traffic_is_over = True
                sub.status = 'traffic_exhausted'
                save_fields.extend(['traffic_is_over', 'status'])
                exhausted += 1

            if sub.total_traffic_usage_byte != used_bytes:
                sub.total_traffic_usage_byte = used_bytes
                updated += 1
                sub.save(update_fields=save_fields)

        except Exception as e:
            logger.error("Failed to sync quota for sub %d (%s): %s", sub.id, sub.username, e)
            failed += 1

    logger.info(
        "Traffic sync complete: %d updated, %d exhausted, %d failed of %d active",
        updated, exhausted, failed, active_subs.count()
    )

    return {
        'updated': updated,
        'exhausted': exhausted,
        'failed': failed,
        'total_active': active_subs.count(),
    }