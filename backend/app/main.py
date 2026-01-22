from fastapi import FastAPI

from backend.app.api.v1.routers.events import router as events_router
from backend.app.api.v1.routers.notifications import router as notifications_router

app = FastAPI(title="Control Backend")

app.include_router(events_router)

app.include_router(notifications_router)
