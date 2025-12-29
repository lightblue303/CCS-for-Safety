# backend/app/services/events_service.py
from sqlalchemy.orm import Session

from backend.app.models.event import Event
from backend.app.schemas.events import EventIngestRequest


def create_event(db: Session, req: EventIngestRequest) -> Event:
    row = Event(
        device_id=req.device.id,
        device_type=req.device.type,
        event_type=req.event.type,
        occurred_at=req.event.occurred_at,
        lat=req.location.lat,
        lng=req.location.lng,
        payload=req.payload,
    )

    db.add(row)      # INSERT 준비
    db.commit()      # 실제 INSERT 실행
    db.refresh(row)  # DB가 만든 id 등 다시 읽기

    return row
