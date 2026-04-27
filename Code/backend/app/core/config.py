# backend/app/core/config.py
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # Database
    DB_HOST: str
    DB_PORT: int = 3306
    DB_USER: str
    DB_PASSWORD: str
    DB_NAME: str
    
    GOOGLE_APPLICATION_CREDENTIALS: str | None = None
    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"


settings = Settings()
