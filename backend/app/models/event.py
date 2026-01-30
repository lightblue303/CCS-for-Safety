from sqlalchemy import Column, BigInteger, String, DateTime, DECIMAL, JSON
from sqlalchemy.sql import func

from backend.app.core.database import Base


class Event(Base):
    __tablename__ = "events"

    id = Column(BigInteger, primary_key=True, autoincrement=True)

    device_key = Column(String(64), nullable=False)
    device_type = Column(String(32), nullable=False)

    event_type = Column(String(64), nullable=False)
    occurred_at = Column(DateTime, nullable=False)
    received_at = Column(DateTime, server_default=func.now(), nullable=False)

    lat = Column(DECIMAL(9, 6), nullable=False)
    lng = Column(DECIMAL(9, 6), nullable=False)

    payload = Column(JSON, nullable=True)

    created_at = Column(DateTime, server_default=func.now(), nullable=False)
