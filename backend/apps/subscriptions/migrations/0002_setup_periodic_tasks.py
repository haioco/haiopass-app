from django.db import migrations


def setup_periodic_tasks(apps, schema_editor):
    IntervalSchedule = apps.get_model('django_celery_beat', 'IntervalSchedule')
    PeriodicTask = apps.get_model('django_celery_beat', 'PeriodicTask')

    schedule, _ = IntervalSchedule.objects.get_or_create(
        every=5,
        period='minutes',
    )

    PeriodicTask.objects.get_or_create(
        name='Sync traffic usage every 5 minutes',
        defaults={
            'task': 'subscriptions.sync_traffic_usage',
            'interval': schedule,
            'enabled': True,
        },
    )


def teardown_periodic_tasks(apps, schema_editor):
    PeriodicTask = apps.get_model('django_celery_beat', 'PeriodicTask')
    PeriodicTask.objects.filter(
        task='subscriptions.sync_traffic_usage',
    ).delete()


class Migration(migrations.Migration):
    dependencies = [
        ('subscriptions', '0001_initial'),
        ('django_celery_beat', '0018_improve_crontab_helptext'),
    ]

    operations = [
        migrations.RunPython(setup_periodic_tasks, teardown_periodic_tasks),
    ]
