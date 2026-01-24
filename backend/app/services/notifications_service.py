# backend/app/services/notifications_service.py
from __future__ import annotations

import time
from datetime import datetime, timezone

from sqlalchemy.orm import Session

from fastapi import HTTPException

from backend.app.core.database import SessionLocal
from backend.app.models.notification import Notification


def _utcnow() -> datetime:
    """UTC timezone-aware now"""
    return datetime.now(timezone.utc)


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
    발송 성공 처리: status=SENT, sent_at=now(UTC)
    """
    n = db.query(Notification).filter(Notification.id == notification_id).one_or_none()
    if n is None:
        raise HTTPException(status_code=404, detail="notification not found")

    n.status = "SENT"
    n.sent_at = _utcnow()
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


def mark_acked(db: Session, *, notification_id: int) -> Notification:
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
    n.ack_at = _utcnow()
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
    - ACK가 없으면 관리자 알림(ADMIN_FCM) 생성(중복 방지)
    - 작업자 알림 status=TIMEOUT 처리
    """

    # 1) ACK 대기
    time.sleep(timeout_seconds)

    # 2) 백그라운드에서는 새 DB 세션을 직접 열어야 함
    db = SessionLocal()
    try:
        worker_n = (
            db.query(Notification)
            .filter(Notification.id == worker_notification_id)
            .one_or_none()
        )

        if worker_n is None:
            return

        # 3) 이미 ACK 처리된 경우 종료
        if worker_n.status == "ACKED" or worker_n.ack_at is not None:
            return

        # 4) 관리자 알림 중복 생성 방지
        existing_admin = (
            db.query(Notification)
            .filter(
                Notification.event_id == worker_n.event_id,
                Notification.channel == "ADMIN_FCM",
            )
            .one_or_none()
        )
        if existing_admin is not None:
            # 이미 관리자 알림이 있으면 작업자만 TIMEOUT 처리하고 종료
            worker_n.status = "TIMEOUT"
            db.commit()
            return

        # 5) 관리자 알림 생성
        admin_n = Notification(
            event_id=worker_n.event_id,
            channel="ADMIN_FCM",
            status="PENDING",
        )
        db.add(admin_n)

        # 6) 작업자 알림 상태 TIMEOUT 처리
        worker_n.status = "TIMEOUT"

        db.commit()

    finally:
        db.close()
