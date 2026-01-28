# backend/app/api/v1/routers/subscriptions.py
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from backend.app.core.deps import get_db
from backend.app.api.v1.schemas.subscriptions import SubscribeRequest, SubscribeResponse
from backend.app.services.subscriptions_service import subscribe_device

router = APIRouter(prefix="/api/v1/devices", tags=["devices"])

@router.post("/{device_key}/subscribe", response_model=SubscribeResponse)
def subscribe(device_key: str, body: SubscribeRequest, db: Session = Depends(get_db)):
    # device_type은 지금 단계에서는 간단히 고정/또는 body로 받아도 됨
    sub = subscribe_device(
        db,
        device_key=device_key,
        device_type="wearable",
        token=body.token,
        role=body.role,
    )
    return {
        "status": "ok",
        "device_key": device_key,
        "subscription_id": sub.id,
    }
