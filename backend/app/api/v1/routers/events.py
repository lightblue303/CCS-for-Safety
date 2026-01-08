from typing import List
from fastapi import APIRouter, Depends, status
from sqlalchemy.orm import Session

from backend.app.services.events_service import get_events
from backend.app.api.v1.schemas.events import EventResponse
from backend.app.schemas.events import EventIngestRequest
from backend.app.core.deps import get_db
from backend.app.services.events_service import create_event

router = APIRouter(prefix="/api/v1/events", tags=["events"])


@router.post("", status_code=status.HTTP_201_CREATED)
def ingest_event(req: EventIngestRequest, db: Session = Depends(get_db)):
    saved = create_event(db, req)
    return {"status": "ok", "event_id": saved.id}

@router.get(
    "",
    response_model=List[EventResponse],
   )
def list_events(
    db: Session = Depends(get_db),
):
    return get_events(db)
