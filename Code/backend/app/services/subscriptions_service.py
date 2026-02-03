# backend/app/services/subscriptions_service.py
from sqlalchemy.orm import Session
from fastapi import HTTPException
from typing import List
from backend.app.models.device import Device
from backend.app.models.push_token import PushToken
from backend.app.models.device_subscription import DeviceSubscription

def subscribe_device(
    db: Session,
    *,
    device_key: str,
    device_type: str,
    token: str,
    role: str,
) -> DeviceSubscription:
    """
    device_key(=events.device_id) 기준으로 디바이스 찾고,
    token 찾아서 구독 생성.

    WORKER 1:1을 “강제”하고 싶으면:
    - 해당 device_key의 WORKER 활성 구독을 찾아 is_active=False 처리 후 새로 생성(또는 교체)
    """
    device = db.query(Device).filter(Device.device_key == device_key).one_or_none()
    if device is None:
        device = Device(device_key=device_key, device_type=device_type)
        db.add(device)
        db.commit()
        db.refresh(device)

    pt = db.query(PushToken).filter(PushToken.token == token, PushToken.is_active == True).one_or_none()
    if pt is None:
        raise HTTPException(status_code=400, detail="push token not registered or inactive")

    # (옵션) WORKER 1:1 강제: 기존 WORKER 활성 구독 비활성화
    if role == "WORKER":
        old = (
            db.query(DeviceSubscription)
            .filter(
                DeviceSubscription.device_id == device.id,
                DeviceSubscription.role == "WORKER",
                DeviceSubscription.is_active == True,
            )
            .all()
        )
        for s in old:
            s.is_active = False

    # upsert 느낌으로: 이미 있으면 활성화만
    sub = (
        db.query(DeviceSubscription)
        .filter(
            DeviceSubscription.device_id == device.id,
            DeviceSubscription.push_token_id == pt.id,
            DeviceSubscription.role == role,
        )
        .one_or_none()
    )
    if sub is None:
        sub = DeviceSubscription(
            device_id=device.id,
            push_token_id=pt.id,
            role=role,
            is_active=True,
        )
        db.add(sub)
    else:
        sub.is_active = True

    db.commit()
    db.refresh(sub)
    return sub

def get_active_tokens_for_device_role(
    db: Session,
    *,
    device_key: str,
    role: str,
) -> List[PushToken]:
    device = db.query(Device).filter(Device.device_key == device_key).one_or_none()
    if device is None:
        return []

    subs = (
        db.query(DeviceSubscription)
        .filter(
            DeviceSubscription.device_id == device.id,
            DeviceSubscription.role == role,
            DeviceSubscription.is_active == True,
        )
        .all()
    )
    if not subs:
        return []

    token_ids = [s.push_token_id for s in subs]
    tokens = (
        db.query(PushToken)
        .filter(PushToken.id.in_(token_ids), PushToken.is_active == True)
        .all()
    )
    return tokens
