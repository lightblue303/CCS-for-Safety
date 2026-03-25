import os
import firebase_admin
from firebase_admin import credentials

def run():
    cred_path = os.getenv("GOOGLE_APPLICATION_CREDENTIALS")
    print("Credential path:", cred_path)

    if not cred_path:
        raise RuntimeError("GOOGLE_APPLICATION_CREDENTIALS is not set")

    cred = credentials.Certificate(cred_path)
    firebase_admin.initialize_app(cred)

    print("✅ Firebase Admin initialized successfully")

if __name__ == "__main__":
    run()
