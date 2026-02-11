from fastapi import FastAPI

from app.api.v1.routers.events import router as events_router
from app.api.v1.routers.notifications import router as notifications_router
from app.api.v1.routers.push_tokens import router as push_tokens_router
from app.api.v1.routers.subscriptions import router as subscriptions_router
from app.core.database import Base, engine

app = FastAPI(title="Control Backend")
@app.on_event("startup")
def on_startup():
    Base.metadata.create_all(bind=engine)
app.include_router(events_router)

app.include_router(notifications_router)

app.include_router(push_tokens_router)

app.include_router(subscriptions_router)
