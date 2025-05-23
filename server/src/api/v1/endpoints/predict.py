from fastapi import APIRouter, Depends, HTTPException
from fastapi.security import APIKeyHeader
from schemas.errors import BaseErrorSchema
from schemas.predict import PredictRequestSchema, PredictResponseSchema
from services.multilanguage_voice_assistant import VoiceAssistant
from core.config import settings

predict_router = APIRouter(prefix="/ai-response", tags=["ai-response"])
header_scheme = APIKeyHeader(name="api-key")


@predict_router.post(
    "/",
    status_code=200,
    responses={
        200: {"model": PredictResponseSchema},
        400: {"model": BaseErrorSchema},
        404: {"model": BaseErrorSchema},
    },
)
async def predict(data: PredictRequestSchema, key: str = Depends(header_scheme)):
    if key != settings.API_KEY:
        raise HTTPException(401, "not authorized")

    assistant = VoiceAssistant(
        provider="mistral_api",
        mistral_api_key=settings.MISTRAL_API_KEY,
        model_name="ministral-3b-latest",
    )

    response = assistant.process_input(data.message)
    return PredictResponseSchema(
        command=response["command"],
        response=response["response"],
        params=response["params"],
    )
