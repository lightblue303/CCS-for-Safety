# backend/app/models/push_token.py
from sqlalchemy import BigInteger, Column, DateTime, String, Boolean
from sqlalchemy.sql import func
from backend.app.core.database import Base

class PushToken(Base):
    __tablename__ = "push_tokens"

    id = Column(BigInteger, primary_key=True, index=True)
    owner_type = Column(String(16), nullable=False, index=True)   # WORKER / ADMIN
    token = Column(String(255), nullable=False, unique=True)
    platform = Column(String(16), nullable=False, default="ANDROID")

    is_active = Column(Boolean, nullable=False, default=True, index=True)
    last_seen_at = Column(DateTime(timezone=False), nullable=True)
    created_at = Column(DateTime(timezone=False), server_default=func.now(), nullable=False)
