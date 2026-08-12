import logging
from datetime import timedelta

from celery import shared_task
from django.utils import timezone

from .models import Subscription
from .services.anti_sanction import anti_sanction_client, generate_username, generate_password
from apps.plans.models import Plan

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
            used_bytes = max(0, sub.total_traffic_byte - remaining_quota)
            used_bytes = min(used_bytes, sub.total_traffic_byte)

            save_fields = ['total_traffic_usage_byte', 'updated_at']

            if used_bytes >= sub.total_traffic_byte and not sub.traffic_is_over:
                sub.traffic_is_over = True
                sub.status = 'traffic_exhausted'
                save_fields.extend(['traffic_is_over', 'status'])
                exhausted += 1

            sub.total_traffic_usage_byte = used_bytes
            sub.save(update_fields=save_fields)
            updated += 1

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


@shared_task(name='subscriptions.activate_subscription')
def activate_subscription(subscription_id):
    try:
        sub = Subscription.objects.select_related('plan').get(id=subscription_id)
    except Subscription.DoesNotExist:
        logger.error("activate_subscription: Subscription %d not found", subscription_id)
        return {'error': 'not_found', 'subscription_id': subscription_id}

    if sub.status not in ('pending',):
        logger.warning("activate_subscription: sub %d status is %s, skipping", sub.id, sub.status)
        return {'error': 'wrong_status', 'subscription_id': sub.id, 'status': sub.status}

    traffic_bytes = sub.plan.traffic_bytes
    resp = anti_sanction_client.add_user(sub.username, sub.password, traffic_bytes)
    if not resp:
        logger.error("activate_subscription: anti_sanction add_user failed for sub %d", sub.id)
        sub.status = 'activation_failed'
        sub.save(update_fields=['status', 'updated_at'])
        return {'error': 'anti_sanction_failed', 'subscription_id': sub.id}

    sub.total_traffic_byte = traffic_bytes
    sub.status = 'active'
    sub.activated_at = timezone.now()
    sub.save(update_fields=['status', 'activated_at', 'total_traffic_byte', 'updated_at'])

    logger.info("activate_subscription: sub %d activated successfully", sub.id)
    return {'success': True, 'subscription_id': sub.id}


@shared_task(name='subscriptions.renew_subscription')
def renew_subscription(subscription_id):
    try:
        sub = Subscription.objects.select_related('plan').get(id=subscription_id)
    except Subscription.DoesNotExist:
        logger.error("renew_subscription: Subscription %d not found", subscription_id)
        return {'error': 'not_found', 'subscription_id': subscription_id}

    plan = sub.plan
    traffic_bytes = plan.traffic_bytes

    resp = anti_sanction_client.renew_user(sub.username, sub.password, traffic_bytes)
    if not resp:
        logger.error("renew_subscription: anti_sanction renew failed for sub %d", sub.id)
        return {'error': 'anti_sanction_failed', 'subscription_id': sub.id}

    sub.total_traffic_byte = traffic_bytes
    sub.total_traffic_usage_byte = 0
    sub.traffic_is_over = False
    sub.status = 'active'
    sub.expired_at = timezone.now() + timedelta(days=plan.duration_days)
    sub.save(update_fields=[
        'total_traffic_byte', 'total_traffic_usage_byte',
        'traffic_is_over', 'status', 'expired_at', 'updated_at',
    ])

    logger.info("renew_subscription: sub %d renewed successfully", sub.id)
    return {'success': True, 'subscription_id': sub.id}
