from fastapi import FastAPI

from backend.app.api.v1.routers.events import router as events_router
from backend.app.api.v1.routers.notifications import router as notifications_router
from backend.app.api.v1.routers.push_tokens import router as push_tokens_router
from backend.app.api.v1.routers.subscriptions import router as subscriptions_router

app = FastAPI(title="Control Backend")

app.include_router(events_router)

app.include_router(notifications_router)

app.include_router(push_tokens_router)

app.include_router(subscriptions_router)
