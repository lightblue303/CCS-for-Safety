from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from datetime import datetime

from app.core.deps import get_db
from app.schemas.worker_status import (
    WorkerStatusRequest,
    WorkerStatusResponse,
)
from app.services.worker_status_service import (
    create_worker_status,
    get_worker_status,
)

router = APIRouter(prefix="/api/v1/worker-status", tags=["worker-status"])


@router.post("")
def create_status(
    payload: WorkerStatusRequest,
    db: Session = Depends(get_db),
):

    return create_worker_status(db, payload)


@router.get("")
def read_status(
    device_key: str | None = None,
    start_date: datetime | None = None,
    end_date: datetime | None = None,
    db: Session = Depends(get_db),
):

    return get_worker_status(
        db,
        device_key=device_key,
        start_date=start_date,
        end_date=end_date,
    )
