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
    occurred_at: datetime = Field(..., description="Event occurrence time in UTC (ISO-8601)")
    severity: str = Field(default="critical", description="info | warn | critical")
    confidence: Optional[float] = Field(
        default=None, ge=0.0, le=1.0, description="Detection confidence (0.0 ~ 1.0)"
    )


# -------------------------
# Location 정보 (어디서)
# -------------------------
class LocationInfo(BaseModel):
    scheme: str = Field(..., example="zone", description="zone | geo")
    site: Optional[str] = Field(default=None, description="Site/factory identifier")
    zone: Optional[str] = Field(default=None, description="Zone/line identifier")
    lat: Optional[float] = Field(default=None, description="Latitude")
    lng: Optional[float] = Field(default=None, description="Longitude")


# -------------------------
# 최종 수신 요청 (ESP32 → 서버)
# -------------------------
class EventIngestRequest(BaseModel):
    device: DeviceInfo
    event: EventInfo
    location: Optional[LocationInfo] = None
    payload: Optional[Dict[str, Any]] = Field(
        default=None,
        description="Optional raw/extra data (sensor metrics, model scores, etc.)",
    )
    
