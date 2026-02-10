# backend/app/services/notifications_service.py
from __future__ import annotations

from datetime import datetime, timezone
from typing import Optional, List

import time
from fastapi import HTTPException
from sqlalchemy.orm import Session

from app.core.database import SessionLocal
from app.models.notification import Notification
from app.services.push_tokens_service import get_active_tokens_for_device_role

from app.models.event import Event

def create_notification(
    db: Session,
    *,
    event_id: int,
    channel: str,
) -> Notification:
    """
    notifications row 생성 (기본 status=PENDING)
    """
    n = Notification(
        event_id=event_id,
        channel=channel,
        status="PENDING",
    )
    db.add(n)
    db.commit()
    db.refresh(n)
    return n

def mark_sent(db: Session, *, notification_id: int) -> Notification:
    """
    발송 성공 처리: status=SENT, sent_at=now
    """
    n = db.query(Notification).filter(Notification.id == notification_id).one_or_none()
    if n is None:
        raise HTTPException(status_code=404, detail="notification not found")

    n.status = "SENT"
    n.sent_at = datetime.now(timezone.utc)
    db.commit()
    db.refresh(n)
    return n

def mark_failed(db: Session, *, notification_id: int) -> Notification:
    """
    발송 실패 처리: status=FAILED
    """
    n = db.query(Notification).filter(Notification.id == notification_id).one_or_none()
    if n is None:
        raise HTTPException(status_code=404, detail="notification not found")

    n.status = "FAILED"
    db.commit()
    db.refresh(n)
    return n

def mark_acked(db: Session, notification_id: int) -> Notification:
    """
    ACK 처리
    """
    n = (
        db.query(Notification)
        .filter(Notification.id == notification_id)
        .one_or_none()
    )

    if n is None:
        raise HTTPException(status_code=404, detail="notification not found")

    n.status = "ACKED"
    n.ack_at = datetime.now(timezone.utc)
    db.commit()
    db.refresh(n)
    return n

def escalate_to_admin_if_unacked(
    *,
    worker_notification_id: int,
    timeout_seconds: int = 30,
) -> None:
    """
    작업자 알림 생성 후 timeout_seconds 동안 ACK 대기
    - ACK가 오면 아무 일도 하지 않음
    - ACK가 없으면 관리자 알림(ADMIN_FCM) 생성
    """
    time.sleep(timeout_seconds)

    db = SessionLocal()
    try:
        worker_n = (
            db.query(Notification)
            .filter(Notification.id == worker_notification_id)
            .one_or_none()
        )
        if worker_n is None:
            return

        # 이미 ACK 처리되었으면 종료
        if worker_n.status == "ACKED" and worker_n.ack_at is not None:
            return

        ev = (
            db.query(Event)
            .filter(Event.id == worker_n.event_id)
            .one_or_none()
        )
        if ev is None:
            return

        # WORKER TIMEOUT 처리
        worker_n.status = "TIMEOUT"
        db.commit()

        # ADMIN 토큰 존재 시 ADMIN 알림 1건 생성
        admin_tokens = get_active_tokens_for_device_role(
            db,
            device_key=ev.device_key,
            role="ADMIN",
        )
        if not admin_tokens:
            return

        db.add(
            Notification(
                event_id=worker_n.event_id,
                channel="ADMIN_FCM",
                status="PENDING",
            )
        )
        db.commit()
    finally:
        db.close()
