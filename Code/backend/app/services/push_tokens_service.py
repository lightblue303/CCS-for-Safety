# backend/app/services/push_tokens_service.py
from datetime import datetime

from sqlalchemy.orm import Session

from app.models.push_token import PushToken


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
    push_token = db.query(PushToken).filter(PushToken.token == token).one_or_none()

    if push_token is None:
        push_token = PushToken(
            owner_type=owner_type,
            token=token,
            platform=platform,
            is_active=True,
            last_seen_at=datetime.utcnow(),
        )
        db.add(push_token)
        db.commit()
        db.refresh(push_token)
        return push_token

    push_token.owner_type = owner_type
    push_token.platform = platform
    push_token.is_active = True
    push_token.last_seen_at = datetime.utcnow()

    db.commit()
    db.refresh(push_token)
    return push_token
