from __future__ import annotations

from pydantic import BaseModel
from typing import Any, List, Dict, Optional
from datetime import datetime

class EventOut(BaseModel):
    id: int
    device_id: str
    device_type: str
    event_type: str
    occurred_at: datetime
    lat: float | None
    lng: float | None
    payload: Any

    class Config:
        from_attributes = True


class EventListResponse(BaseModel):
    items: List[EventOut]
    count: int

# ---------
# Request (POST /events)
# ---------
class DeviceIn(BaseModel):
    id: str
    type: str

class EventIn(BaseModel):
    type: str
    occurred_at: datetime

class LocationIn(BaseModel):
    lat: float
    lng: float

class EventCreateRequest(BaseModel):
    device: DeviceIn
    event: EventIn
    location: LocationIn
    payload: Optional[Dict[str, Any]] = None


# ---------
# Response (GET /events, POST 응답 확장 시에도 사용 가능)
# ---------
class EventResponse(BaseModel):
    id: int
    device_id: str
    device_type: str
    event_type: str
    occurred_at: datetime
    lat: Optional[float] = None
    lng: Optional[float] = None
    payload: Optional[Dict[str, Any]] = None
    created_at: Optional[datetime] = None

    class Config:
        from_attributes = True
