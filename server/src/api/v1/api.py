from fastapi import APIRouter

from api.v1.endpoints.health import health_router
from api.v1.endpoints.predict import predict_router

api_router = APIRouter(prefix="")
api_router.include_router(predict_router)
api_router.include_router(health_router)
