# backend/app/services/notifications_service.py
from datetime import datetime, timezone

from fastapi import HTTPException
from sqlalchemy.orm import Session

from app.models.event import Event
from app.models.notification import Notification
from app.services.fcm_service import send_admin_push
from app.services.subscriptions_service import get_active_tokens_for_device_role


def create_notification(
    db: Session,
    *,
    event_id: int,
    channel: str,
) -> Notification:
    """
    notifications row 생성 (기본 status=PENDING)
    """
    notification = Notification(
        event_id=event_id,
        channel=channel,
        status="PENDING",
    )
    db.add(notification)
    db.commit()
    db.refresh(notification)
    return notification


def mark_sent(db: Session, *, notification_id: int) -> Notification:
    """
    발송 성공 처리: status=SENT, sent_at=now
    """
    notification = (
        db.query(Notification)
        .filter(Notification.id == notification_id)
        .one_or_none()
    )
    if notification is None:
        raise HTTPException(status_code=404, detail="notification not found")

    notification.status = "SENT"
    notification.sent_at = datetime.now(timezone.utc)
    db.commit()
    db.refresh(notification)
    return notification


def mark_failed(db: Session, *, notification_id: int) -> Notification:
    """
    발송 실패 처리: status=FAILED
    """
    notification = (
        db.query(Notification)
        .filter(Notification.id == notification_id)
        .one_or_none()
    )
    if notification is None:
        raise HTTPException(status_code=404, detail="notification not found")

    notification.status = "FAILED"
    db.commit()
    db.refresh(notification)
    return notification


def process_admin_notification_for_event(db: Session, *, event: Event) -> dict:
    """
    - 이벤트 저장 후 ADMIN 구독 토큰 조회
    - ADMIN_FCM notification 생성
    - 실제 FCM 전송
    - 성공/실패 상태 반영
    """
    tokens = get_active_tokens_for_device_role(
        db,
        device_key=event.device_key,
        role="ADMIN",
    )

    notification = create_notification(
        db,
        event_id=event.id,
        channel="ADMIN_FCM",
    )

    if not tokens:
        mark_failed(db, notification_id=notification.id)
        return {
            "notification_id": notification.id,
            "notification_status": "FAILED",
            "sent_count": 0,
            "failed_count": 0,
            "reason": "no active admin tokens",
        }

    send_result = send_admin_push(event=event, tokens=tokens)

    if send_result["success_count"] > 0:
        mark_sent(db, notification_id=notification.id)
        final_status = "SENT"
    else:
        mark_failed(db, notification_id=notification.id)
        final_status = "FAILED"

    return {
        "notification_id": notification.id,
        "notification_status": final_status,
        "sent_count": send_result["success_count"],
        "failed_count": send_result["failure_count"],
    }
