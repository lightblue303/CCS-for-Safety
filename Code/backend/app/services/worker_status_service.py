from sqlalchemy.orm import Session
from app.models.worker_status_log import WorkerStatusLog
from app.models.device import Device


def create_worker_status(db: Session, payload):

    device = (
        db.query(Device)
        .filter(Device.device_key == payload.device_key)
        .first()
    )

    log = WorkerStatusLog(
        device_key=payload.device_key,
        device_fk=device.id if device else None,
        status=payload.status,
        occurred_at=payload.occurred_at,
        lat=payload.location.lat if payload.location else None,
        lng=payload.location.lng if payload.location else None,
    )

    db.add(log)
    db.commit()
    db.refresh(log)

    return log

def get_worker_status(
    db: Session,
    device_key: str | None = None,
    start_date=None,
    end_date=None,
):

    query = db.query(WorkerStatusLog)

    if device_key:
        query = query.filter(
            WorkerStatusLog.device_key == device_key
        )

    if start_date:
        query = query.filter(
            WorkerStatusLog.occurred_at >= start_date
        )

    if end_date:
        query = query.filter(
            WorkerStatusLog.occurred_at <= end_date
        )

    return query.order_by(
        WorkerStatusLog.occurred_at.desc()
    ).all()
