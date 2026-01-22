# backend/app/services/notifications_service.py
from __future__ import annotations

from datetime import datetime
from typing import Optional

from sqlalchemy.orm import Session

from backend.app.models.notification import Notification


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
    n = db.query(Notification).filter(Notification.id == notification_id).one()
    n.status = "SENT"
    n.sent_at = datetime.utcnow()
    db.commit()
    db.refresh(n)
    return n


def mark_failed(db: Session, *, notification_id: int) -> Notification:
    """
    발송 실패 처리: status=FAILED
    """
    n = db.query(Notification).filter(Notification.id == notification_id).one()
    n.status = "FAILED"
    db.commit()
    db.refresh(n)
    return n


def mark_acked(db: Session, *, notification_id: int) -> Notification:
    """
    작업자 확인(ACK) 처리: status=ACKED, ack_at=now
    """
    n = db.query(Notification).filter(Notification.id == notification_id).one()

    # 이미 ACKED면 그대로 반환 (멱등성)
    if n.status != "ACKED":
        n.status = "ACKED"
        n.ack_at = datetime.utcnow()
        db.commit()
        db.refresh(n)

    return n
