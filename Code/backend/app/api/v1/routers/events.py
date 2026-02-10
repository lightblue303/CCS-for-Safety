from typing import List
from fastapi import APIRouter, Depends, status, BackgroundTasks, HTTPException
from sqlalchemy.orm import Session

from app.services.events_service import get_events
from app.api.v1.schemas.events import EventResponse
from app.schemas.events import EventIngestRequest
from app.core.deps import get_db
from app.services.events_service import create_event
from app.services.notifications_service import (create_notification, mark_sent, escalate_to_admin_if_unacked,)
from app.services.push_tokens_service import (
    get_active_tokens_for_device_role,
)

router = APIRouter(prefix="/api/v1/events", tags=["events"])


@router.post("", status_code=status.HTTP_201_CREATED)
def ingest_event(
    req: EventIngestRequest,
    background_tasks: BackgroundTasks,
    db: Session = Depends(get_db),
):
    # 1) 이벤트 저장
    saved = create_event(db, req)

    # 2) device_key 기준 WORKER 구독 토큰 존재 확인
    worker_tokens = get_active_tokens_for_device_role(
        db,
        device_key=saved.device_key,
        role="WORKER",
    )
    if not worker_tokens:
        # 여기 정책은 선택:
        # WORKER가 없으면 즉시 ADMIN으로만 보낸다(=바로 ADMIN notification 생성)
        admin_n = create_notification(db, event_id=saved.id, channel="ADMIN_FCM")
        return {"status": "ok", "event_id": saved.id, "admin_notification_id": admin_n.id, "note": "no worker subscription"}

    # 3) 작업자 알림 row 생성
    worker_n = create_notification(db, event_id=saved.id, channel="WORKER_FCM")

    # 4) timeout 백그라운드 등록 (ACK 없으면 ADMIN 알림 row 생성)
    background_tasks.add_task(
        escalate_to_admin_if_unacked,
        worker_notification_id=worker_n.id,
        timeout_seconds=30,
    )

    return {"status": "ok", "event_id": saved.id, "worker_notification_id": worker_n.id}

@router.get(
    "",
    response_model=List[EventResponse],
   )
def list_events(
    db: Session = Depends(get_db),
):
    return get_events(db)
