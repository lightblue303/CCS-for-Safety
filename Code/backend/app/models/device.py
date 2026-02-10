# backend/app/models/device.py
from sqlalchemy import BigInteger, Column, DateTime, String
from sqlalchemy.sql import func
from app.core.database import Base

class Device(Base):
    __tablename__ = "devices"

    id = Column(BigInteger, primary_key=True, index=True)
    device_key = Column(String(64), nullable=False, unique=True, index=True)
    device_type = Column(String(32), nullable=False)

    created_at = Column(DateTime(timezone=False), server_default=func.now(), nullable=False)
