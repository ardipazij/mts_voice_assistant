from datetime import tzinfo
from zoneinfo import ZoneInfo

from pydantic import field_validator
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    LOG_LEVEL: str = "INFO"
    APP_NAME: str = "shop-service"
    LOG_PATH: str = "/tmp/logs/log.log"
    APP_HOST: str = "127.0.0.1"
    APP_PORT: str = "8000"

    DEFAULT_TIMEZONE: tzinfo | str = "Europe/Moscow"
    DEFAULT_ENCODING: str = "utf-8"

    API_KEY: str
    MISTRAL_API_KEY: str

    @field_validator("DEFAULT_TIMEZONE")
    @classmethod
    def assemble_timezone(cls, v: str, _) -> ZoneInfo:
        return ZoneInfo(v)


settings = Settings()  # type: ignore
