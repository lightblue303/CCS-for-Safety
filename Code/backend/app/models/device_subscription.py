# backend/app/models/device_subscription.py
from sqlalchemy import BigInteger, Column, DateTime, String, Boolean, ForeignKey
from sqlalchemy.sql import func
from app.core.database import Base

class DeviceSubscription(Base):
    __tablename__ = "device_subscriptions"

    id = Column(BigInteger, primary_key=True, index=True)
    device_id = Column(BigInteger, ForeignKey("devices.id"), nullable=False, index=True)
    push_token_id = Column(BigInteger, ForeignKey("push_tokens.id"), nullable=False, index=True)

    role = Column(String(16), nullable=False)  # WORKER / ADMIN
    is_active = Column(Boolean, nullable=False, default=True)
    created_at = Column(DateTime(timezone=False), server_default=func.now(), nullable=False)
