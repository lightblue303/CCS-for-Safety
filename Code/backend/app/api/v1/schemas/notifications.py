# backend/app/api/v1/schemas/notifications.py
from pydantic import BaseModel


class NotificationAckRequest(BaseModel):
    result: str = "OK"
