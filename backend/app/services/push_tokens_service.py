# backend/app/services/push_tokens_service.py
from datetime import datetime
from sqlalchemy.orm import Session
from fastapi import HTTPException

from backend.app.models.push_token import PushToken
from backend.app.models.device_subscription import DeviceSubscription
from backend.app.models.device import Device

def register_push_token(
    db: Session,
    *,
    owner_type: str,
    token: str,
    platform: str = "ANDROID",
) -> PushToken:
    """
    토큰 upsert:
    - 이미 있으면 last_seen_at 갱신 + active 유지
    - 없으면 생성
    """
    pt = db.query(PushToken).filter(PushToken.token == token).one_or_none()

    if pt is None:
        pt = PushToken(
            owner_type=owner_type,
            token=token,
            platform=platform,
            is_active=True,
            last_seen_at=datetime.utcnow(),
        )
        db.add(pt)
        db.commit()
        db.refresh(pt)
        return pt

    # 기존 토큰
    pt.owner_type = owner_type
    pt.platform = platform
    pt.is_active = True
    pt.last_seen_at = datetime.utcnow()
    db.commit()
    db.refresh(pt)
    return pt

def get_active_tokens_for_device_role(
    db: Session,
    *,
    device_key: str, 
    role: str,
) -> list[str]:
    """
    device_key + role(WORKER / ADMIN)에 해당하는 활성 FCM 토큰 목록 조회
    - events는 device_key(string)를 가지고 있고
    - subscriptions는 devices.id(BigInteger FK)를 가지므로
      Device를 join해서 매핑한다.
    """
    rows = (
        db.query(PushToken.token)
        .join(DeviceSubscription, PushToken.id == DeviceSubscription.push_token_id)
        .join(Device, Device.id == DeviceSubscription.device_id)
        .filter(
            Device.device_key == device_key,
            DeviceSubscription.role == role,
            DeviceSubscription.is_active == True, 
            PushToken.is_active == True,
        )
        .all()
    )
    return [r[0] for r in rows]
