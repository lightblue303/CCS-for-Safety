# backend/app/api/v1/routers/notifications.py
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.core.deps import get_db
from app.api.v1.schemas.notifications import NotificationAckRequest
from app.services.notifications_service import mark_acked

router = APIRouter(prefix="/api/v1/notifications", tags=["notifications"])


@router.post("/{notification_id}/ack")
def ack_notification(
    notification_id: int,
    body: NotificationAckRequest,
    db: Session = Depends(get_db),
):
    n = mark_acked(db, notification_id=notification_id)
    return {
        "status": "ok",
        "notification_id": n.id,
        "new_status": n.status,
    }
