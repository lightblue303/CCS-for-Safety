from __future__ import annotations

from datetime import datetime
from typing import Any, Dict, Optional

from pydantic import BaseModel, Field


# -------------------------
# Device 정보 (누가 보냈는지)
# -------------------------
class DeviceInfo(BaseModel):
    id: str = Field(..., description="ESP32 device identifier (e.g. wearable-001)")
    type: str = Field(..., description="Device type (e.g. wearable)")


# -------------------------
# Event 정보 (무슨 일이, 언제)
# -------------------------
class EventInfo(BaseModel):
    type: str = Field(..., example="FALL_DETECTED", description="Event type")
    occurred_at: datetime = Field(
        ..., description="Event occurrence time in UTC (ISO-8601)"
    )
    severity: str = Field(
        default="critical", description="info | warn | critical"
    )


# -------------------------
# Location 정보 (지도 표시용 좌표)
# -------------------------
class LocationInfo(BaseModel):
    lat: float = Field(..., description="Latitude")
    lng: float = Field(..., description="Longitude")


# -------------------------
# 최종 이벤트 수신 모델
# -------------------------
class EventIngestRequest(BaseModel):
    device: DeviceInfo
    event: EventInfo
    location: LocationInfo
    payload: Optional[Dict[str, Any]] = Field(
        default=None,
        description="Optional raw/extra data (sensor metrics, model scores, etc.)",
    )
