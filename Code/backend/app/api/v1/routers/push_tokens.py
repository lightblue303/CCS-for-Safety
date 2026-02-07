# backend/app/api/v1/routers/push_tokens.py
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from backend.app.core.deps import get_db
from backend.app.api.v1.schemas.push_tokens import PushTokenRegisterRequest, PushTokenRegisterResponse
from backend.app.services.push_tokens_service import register_push_token

router = APIRouter(prefix="/api/v1/push-tokens", tags=["push-tokens"])

@router.post("/register", response_model=PushTokenRegisterResponse)
def register(body: PushTokenRegisterRequest, db: Session = Depends(get_db)):
    pt = register_push_token(
        db,
        owner_type=body.owner_type,
        token=body.token,
        platform=body.platform,
    )
    return {
        "status": "ok",
        "push_token_id": pt.id,
        "is_active": bool(pt.is_active),
    }
