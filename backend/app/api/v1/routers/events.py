from typing import List
from fastapi import APIRouter, Depends, status
from sqlalchemy.orm import Session

from backend.app.services.events_service import get_events
from backend.app.api.v1.schemas.events import EventResponse
from backend.app.schemas.events import EventIngestRequest
from backend.app.core.deps import get_db
from backend.app.services.events_service import create_event
from backend.app.services.notifications_service import create_notification, mark_sent

router = APIRouter(prefix="/api/v1/events", tags=["events"])


@router.post("", status_code=status.HTTP_201_CREATED)
def ingest_event(req: EventIngestRequest, db: Session = Depends(get_db)):
    saved = create_event(db, req)

    # 1) WORKER 알림 row 생성
    worker_n = create_notification(db, event_id=saved.id, channel="WORKER_FCM")

    # 2) (지금은 stub) 실제 FCM 연동 전이므로 "발송 성공"으로 기록
    #    나중에 firebase-admin 붙이면 여기서 send() 성공/실패에 따라 mark_sent/mark_failed로 분기
    worker_n = mark_sent(db, notification_id=worker_n.id)

    return {
        "status": "ok",
        "event_id": saved.id,
        "worker_notification_id": worker_n.id,
    }

@router.get(
    "",
    response_model=List[EventResponse],
   )
def list_events(
    db: Session = Depends(get_db),
):
    return get_events(db)
