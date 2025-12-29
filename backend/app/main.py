from fastapi import FastAPI

from backend.app.api.v1.routers.events import router as events_router

app = FastAPI(title="Control Backend")

app.include_router(events_router)
