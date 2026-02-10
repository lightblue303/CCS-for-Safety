# backend/app/services/events_service.py
from sqlalchemy.orm import Session
from app.models.event import Event
from app.schemas.events import EventIngestRequest

# POST
def create_event(db: Session, req: EventIngestRequest):
    event = Event(
        device_key=req.device.device_key,
        device_type=req.device.type,
        event_type=req.event.type,
        occurred_at=req.event.occurred_at,
        lat=req.location.lat if req.location else None,
        lng=req.location.lng if req.location else None,
        payload=req.payload,
    )
    db.add(event)
    db.commit()
    db.refresh(event)
    return event


# GET
def get_events(db: Session, limit: int = 50):
    return (
        db.query(Event)
        .order_by(Event.occurred_at.desc())
        .limit(limit)
        .all()
    )
