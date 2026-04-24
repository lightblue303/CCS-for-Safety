from datetime import datetime
from enum import Enum

from pydantic import BaseModel


class WorkerStatusEnum(str, Enum):
    START = "START"
    PAUSE = "PAUSE"
    END = "END"


class Location(BaseModel):
    lat: float
    lng: float


class WorkerStatusRequest(BaseModel):
    device_key: str
    status: WorkerStatusEnum
    occurred_at: datetime
    location: Location | None = None


class WorkerStatusResponse(BaseModel):
    id: int
    device_key: str
    status: WorkerStatusEnum
    occurred_at: datetime

    class Config:
        from_attributes = True
