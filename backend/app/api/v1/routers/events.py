from fastapi import APIRouter, status

from backend.app.schemas.events import EventIngestRequest

router = APIRouter(prefix="/api/v1/events", tags=["events"])


@router.post("", status_code=status.HTTP_201_CREATED)
def ingest_event(req: EventIngestRequest):
    """
    ESP32 등 디바이스가 보내는 이벤트를 수신한다.
    (현재 단계: DB 저장 없이 수신/검증/응답만)
    """
    # 지금은 DB가 없으므로 로그만 남김 (추후 events 저장으로 교체)
    print("[EVENT RECEIVED]")
    print("device:", req.device.model_dump())
    print("event:", req.event.model_dump())
    print("location:", None if req.location is None else req.location.model_dump())
    print("payload:", req.payload)

    return {
        "status": "ok",
        "message": "event received",
    }
