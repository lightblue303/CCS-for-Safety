# backend/app/services/push_tokens_service.py
from datetime import datetime
from sqlalchemy.orm import Session
from fastapi import HTTPException

from backend.app.models.push_token import PushToken

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
