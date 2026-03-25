from typing import List

from fastapi import APIRouter, Depends, status
from sqlalchemy.orm import Session

from app.api.v1.schemas.events import EventResponse
from app.core.deps import get_db
from app.schemas.events import EventIngestRequest
from app.services.events_service import create_event, get_events
from app.services.notifications_service import process_admin_notification_for_event

router = APIRouter(prefix="/api/v1/events", tags=["events"])


@router.post("", status_code=status.HTTP_201_CREATED)
def ingest_event(
    req: EventIngestRequest,
    db: Session = Depends(get_db),
):
    # 1) 이벤트 저장
    saved = create_event(db, req)

    # 2) 관리자 알림 생성 + FCM 전송
    notify_result = process_admin_notification_for_event(db, event=saved)

    return {
        "status": "ok",
        "event_id": saved.id,
        "notification_id": notify_result["notification_id"],
        "notification_status": notify_result["notification_status"],
        "sent_count": notify_result["sent_count"],
        "failed_count": notify_result["failed_count"],
        "reason": notify_result.get("reason"),
    }


@router.get("", response_model=List[EventResponse])
def list_events(
    db: Session = Depends(get_db),
):
    return get_events(db)
