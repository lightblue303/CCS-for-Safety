from sqlalchemy import Column, String, DateTime, ForeignKey, Double
from sqlalchemy.sql import func
from sqlalchemy.dialects.mysql import BIGINT

from app.core.database import Base


class WorkerStatusLog(Base):
    __tablename__ = "worker_status_logs"

    id = Column(
        BIGINT(unsigned=True),
        primary_key=True,
        index=True
    )

    device_key = Column(
        String(255),
        nullable=False
    )

    device_fk = Column(
        BIGINT(unsigned=True),
        ForeignKey("devices.id"),
        nullable=True
    )

    status = Column(
        String(20),
        nullable=False
    )

    occurred_at = Column(
        DateTime(timezone=True),
        nullable=False
    )

    lat = Column(
        Double,
        nullable=True
    )

    lng = Column(
        Double,
        nullable=True
    )

    created_at = Column(
        DateTime(timezone=True),
        server_default=func.now()
    )
