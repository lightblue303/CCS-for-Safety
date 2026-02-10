# backend/app/models/notification.py
from sqlalchemy import BigInteger, Column, DateTime, String, ForeignKey
from sqlalchemy.sql import func
from app.core.database import Base


class Notification(Base):
    __tablename__ = "notifications"

    id = Column(BigInteger, primary_key=True, index=True)
    event_id = Column(BigInteger, ForeignKey("events.id"), nullable=False, index=True)

    channel = Column(String(32), nullable=False)

    # DB 기본값까지 보장(권장)
    status = Column(String(32), nullable=False, server_default="PENDING")

    # MySQL 기준: timezone=True는 의미가 제한적이라 단순 DateTime 권장
    sent_at = Column(DateTime, nullable=True)
    ack_at = Column(DateTime, nullable=True)

    created_at = Column(DateTime, server_default=func.now(), nullable=False)
