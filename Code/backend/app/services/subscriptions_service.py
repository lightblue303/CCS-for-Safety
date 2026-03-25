# backend/app/services/subscriptions_service.py
from fastapi import HTTPException
from sqlalchemy.orm import Session

from app.models.device import Device
from app.models.device_subscription import DeviceSubscription
from app.models.push_token import PushToken


def subscribe_device(
    db: Session,
    *,
    device_key: str,
    device_type: str,
    token: str,
    role: str,
) -> DeviceSubscription:
    """
    device_key 기준으로 디바이스를 찾고,
    등록된 push token을 찾아 구독을 생성/활성화한다.

    정책:
    - WORKER는 1:1 강제 가능: 기존 활성 WORKER 구독은 비활성화
    - ADMIN은 다수 구독 허용
    """
    device = db.query(Device).filter(Device.device_key == device_key).one_or_none()
    if device is None:
        device = Device(device_key=device_key, device_type=device_type)
        db.add(device)
        db.commit()
        db.refresh(device)

    push_token = (
        db.query(PushToken)
        .filter(
            PushToken.token == token,
            PushToken.is_active == True,
        )
        .one_or_none()
    )
    if push_token is None:
        raise HTTPException(
            status_code=400,
            detail="push token not registered or inactive",
        )

    # WORKER 1:1 정책 유지
    if role == "WORKER":
        old_subscriptions = (
            db.query(DeviceSubscription)
            .filter(
                DeviceSubscription.device_id == device.id,
                DeviceSubscription.role == "WORKER",
                DeviceSubscription.is_active == True,
            )
            .all()
        )
        for sub in old_subscriptions:
            sub.is_active = False

    subscription = (
        db.query(DeviceSubscription)
        .filter(
            DeviceSubscription.device_id == device.id,
            DeviceSubscription.push_token_id == push_token.id,
            DeviceSubscription.role == role,
        )
        .one_or_none()
    )

    if subscription is None:
        subscription = DeviceSubscription(
            device_id=device.id,
            push_token_id=push_token.id,
            role=role,
            is_active=True,
        )
        db.add(subscription)
    else:
        subscription.is_active = True

    db.commit()
    db.refresh(subscription)
    return subscription


def get_active_tokens_for_device_role(
    db: Session,
    *,
    device_key: str,
    role: str,
) -> list[str]:
    """
    device_key + role(WORKER / ADMIN)에 해당하는 활성 FCM 토큰 목록 조회
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

    return [row[0] for row in rows]
