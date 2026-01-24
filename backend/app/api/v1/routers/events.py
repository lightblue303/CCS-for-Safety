from typing import List
from fastapi import APIRouter, Depends, status, BackgroundTasks
from sqlalchemy.orm import Session

from backend.app.services.events_service import get_events
from backend.app.api.v1.schemas.events import EventResponse
from backend.app.schemas.events import EventIngestRequest
from backend.app.core.deps import get_db
from backend.app.services.events_service import create_event
from backend.app.services.notifications_service import (create_notification, mark_sent, escalate_to_admin_if_unacked,)

router = APIRouter(prefix="/api/v1/events", tags=["events"])


@router.post("", status_code=status.HTTP_201_CREATED)
def ingest_event(
    req: EventIngestRequest,
    background_tasks: BackgroundTasks,
    db: Session = Depends(get_db),
):
    # 1) 이벤트 저장
    saved = create_event(db, req)

    # 2) 작업자 알림 row 생성 (DB)
    worker_n = create_notification(
        db,
        event_id=saved.id,
        channel="WORKER_FCM",
    )

    # 3) 타임아웃 백그라운드 작업 등록
    #    - timeout_seconds 후 ack 없으면 ADMIN_FCM 알림 row 생성 + WORKER TIMEOUT 처리
    background_tasks.add_task(
        escalate_to_admin_if_unacked,
        worker_notification_id=worker_n.id,
        timeout_seconds=30,  # 필요 시 10초로 낮춰 테스트
    )

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
