# backend/app/api/v1/schemas/push_tokens.py
from pydantic import BaseModel, Field

class PushTokenRegisterRequest(BaseModel):
    owner_type: str = Field(..., examples=["WORKER", "ADMIN"])
    token: str
    platform: str = Field(default="ANDROID")

class PushTokenRegisterResponse(BaseModel):
    status: str
    push_token_id: int
    is_active: bool
