# backend/app/api/v1/schemas/subscriptions.py
from pydantic import BaseModel, Field

class SubscribeRequest(BaseModel):
    token: str
    role: str = Field(..., examples=["WORKER", "ADMIN"])

class SubscribeResponse(BaseModel):
    status: str
    device_key: str
    subscription_id: int
