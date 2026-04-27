#backend/app/services/fcm_service.py
import os

import firebase_admin
from firebase_admin import credentials, messaging

from app.models.event import Event
from app.core.config import settings

def get_firebase_app():
    """
    Firebase Admin app singleton 초기화/재사용
    """
    try:
        return firebase_admin.get_app()
    except ValueError:
        cred_path = settings.GOOGLE_APPLICATION_CREDENTIALS
        if not cred_path:
            raise RuntimeError("GOOGLE_APPLICATION_CREDENTIALS is not set")

        cred = credentials.Certificate(cred_path)
        return firebase_admin.initialize_app(cred)


def build_admin_payload(event: Event) -> tuple[str, str, dict[str, str]]:
    """
    관리자 푸시용 제목/본문/data payload 생성
    """
    title = f"[{event.event_type}] 위험 이벤트 감지"
    body = f"device={event.device_key}, occurred_at={event.occurred_at}"

    data = {
        "event_id": str(event.id),
        "device_key": event.device_key,
        "device_type": event.device_type,
        "event_type": event.event_type,
        "occurred_at": event.occurred_at.isoformat() if event.occurred_at else "",
        "lat": str(event.lat) if event.lat is not None else "",
        "lng": str(event.lng) if event.lng is not None else "",
    }

    return title, body, data


def send_admin_push(*, event: Event, tokens: list[str]) -> dict:
    """
    ADMIN 대상 multicast push 전송
    """
    if not tokens:
        return {
            "success_count": 0,
            "failure_count": 0,
        }

    get_firebase_app()

    title, body, data = build_admin_payload(event)

    message = messaging.MulticastMessage(
        notification=messaging.Notification(
            title=title,
            body=body,
        ),
        data=data,
        tokens=tokens,
        android=messaging.AndroidConfig(priority="high"),
    )

    response = messaging.send_each_for_multicast(message)

    errors = []
    for idx, resp in enumerate(response.responses):
        if not resp.success:
            error_msg = str(resp.exception)
            print(f"FCM failed token_index={idx}, token={tokens[idx][:20]}..., error={error_msg}")
            errors.append({
                "token_index": idx,
                "token_prefix": tokens[idx][:20],
                "error": error_msg,
            })

    print(
        f"FCM result: success={response.success_count}, "
        f"failure={response.failure_count}, errors={errors}"
    )

    return {
        "success_count": response.success_count,
        "failure_count": response.failure_count,
        "errors": errors,
    }
