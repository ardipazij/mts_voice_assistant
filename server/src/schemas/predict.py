from pydantic import BaseModel


class PredictRequestSchema(BaseModel):
    message: str


class PredictResponseSchema(BaseModel):
    command: str
    response: str
    params: dict
