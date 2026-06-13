"""
AivisSpeech Engine TTS Bridge Service
=====================================
桥接服务：将 Java 后端 (port 8080) 的 TTS 请求转发到 AivisSpeech Engine (port 10101)

AivisSpeech Engine API 流程：
  1. POST /audio_query?speaker={style_id}  (body: text)  → 返回 AudioQuery JSON
  2. POST /synthesis?speaker={style_id}    (body: AudioQuery JSON) → 返回 WAV bytes

启动方式：
  python tts_bridge.py
  或
  uvicorn tts_bridge:app --host 127.0.0.1 --port 5000
"""

import asyncio
import logging
import time
from contextlib import asynccontextmanager

import httpx
from fastapi import FastAPI, HTTPException
from fastapi.responses import Response
from pydantic import BaseModel

# ==================== 配置 ====================

AIVISSPEECH_ENGINE_URL = "http://127.0.0.1:10101"
BRIDGE_HOST = "127.0.0.1"
BRIDGE_PORT = 5000

# 日语字符检测阈值：如果文本中日语假名/汉字占比超过此值，直接使用原文
# 如果低于此值（即中文为主），则追加一句提示让文本更适合日语 TTS
JAPANESE_CHAR_THRESHOLD = 0.3

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
logger = logging.getLogger("tts_bridge")


# ==================== 数据模型 ====================

class TtsRequest(BaseModel):
    text: str
    voice: str = "default"
    format: str = "wav"


# ==================== 生命周期 ====================

@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info(f"TTS Bridge starting on {BRIDGE_HOST}:{BRIDGE_PORT}")
    logger.info(f"AivisSpeech Engine URL: {AIVISSPEECH_ENGINE_URL}")
    yield
    logger.info("TTS Bridge shutting down")


app = FastAPI(title="TTS Bridge for AivisSpeech Engine", lifespan=lifespan)


# ==================== 工具函数 ====================

async def get_speakers(client: httpx.AsyncClient) -> list[dict]:
    """获取可用话者列表及其 style_id"""
    try:
        resp = await client.get(f"{AIVISSPEECH_ENGINE_URL}/speakers")
        resp.raise_for_status()
        return resp.json()
    except Exception:
        return []


async def get_default_style_id(client: httpx.AsyncClient) -> int | None:
    """获取第一个可用话者的第一个 style_id"""
    speakers = await get_speakers(client)
    for speaker in speakers:
        styles = speaker.get("styles", [])
        if styles:
            return styles[0].get("id")
        # 有些 API 版本的结构可能不同
        for style in speaker.get("style_infos", []):
            return style.get("id")
    return None


def contains_japanese(text: str) -> bool:
    """检测文本是否包含足够的日语字符"""
    japanese_count = 0
    total_count = len(text)
    if total_count == 0:
        return False

    for ch in text:
        # 平假名 U+3040-U+309F
        if '\u3040' <= ch <= '\u309f':
            japanese_count += 1
        # 片假名 U+30A0-U+30FF
        elif '\u30a0' <= ch <= '\u30ff':
            japanese_count += 1
        # CJK 统一汉字（日语也会用到）
        elif '\u4e00' <= ch <= '\u9fff':
            japanese_count += 1
        # 日语标点
        elif ch in '。、！？〜・「」『』（）':
            japanese_count += 1

    ratio = japanese_count / total_count
    logger.info(f"Japanese char ratio: {ratio:.2f} ({japanese_count}/{total_count})")
    return ratio >= JAPANESE_CHAR_THRESHOLD


def ensure_japanese_text(text: str) -> str:
    """
    如果文本主要是中文，添加提示让 TTS 输出更自然
    注意：AivisSpeech Engine 只支持日语 TTS，中文输入会发音不正确
    """
    if contains_japanese(text):
        return text.strip()

    # 文本不是日语，添加朗读提示（这只是一个临时方案）
    # 理想情况下，应该通过上层 Agent 确保传入的是日语文本
    logger.warning(
        "Text does not contain enough Japanese characters! "
        "AivisSpeech Engine only supports Japanese TTS. "
        "The output may sound incorrect."
    )
    return text.strip()


# ==================== 并发控制 ====================

tts_semaphore = asyncio.Semaphore(1)


# ==================== API 路由 ====================

@app.get("/health")
async def health():
    """健康检查：检测 AivisSpeech Engine 是否在线"""
    async with httpx.AsyncClient(timeout=5.0, trust_env=False) as client:
        try:
            resp = await client.get(f"{AIVISSPEECH_ENGINE_URL}/version")
            if resp.status_code == 200:
                version_data = resp.json()
                return {
                    "status": "ok",
                    "aivisspeech_engine": "connected",
                    "version": version_data,
                }
        except Exception as e:
            logger.warning(f"Health check failed: {e}")

    return {
        "status": "degraded",
        "aivisspeech_engine": "disconnected",
        "message": f"Cannot reach AivisSpeech Engine at {AIVISSPEECH_ENGINE_URL}",
    }


@app.post("/tts")
async def synthesize_speech(request: TtsRequest):
    """
    语音合成接口
    Java 后端调用: POST /tts  body: {"text": "...", "voice": "style_id"}
    返回: WAV 音频文件的二进制数据
    """
    t_start = time.monotonic()
    text = request.text.strip()
    if not text:
        raise HTTPException(status_code=400, detail="Text is empty")

    text = ensure_japanese_text(text)
    logger.info(f"[TTS] Request received: voice={request.voice}, text_len={len(text)}")

    async with tts_semaphore:
        logger.info(f"[TTS] Semaphore acquired, starting synthesis...")
        async with httpx.AsyncClient(timeout=120.0, trust_env=False) as client:
            # 确定 style_id
            style_id = request.voice
            if style_id == "default" or style_id == "lingyin":
                auto_id = await get_default_style_id(client)
                if auto_id is not None:
                    style_id = str(auto_id)
                    logger.info(f"Using auto-detected style_id: {style_id}")
                else:
                    raise HTTPException(
                        status_code=503,
                        detail="No speaker available in AivisSpeech Engine",
                    )
            else:
                # 尝试将 voice 转为 int
                try:
                    int(style_id)
                except ValueError:
                    auto_id = await get_default_style_id(client)
                    if auto_id is not None:
                        style_id = str(auto_id)

            logger.info(f"TTS request: style_id={style_id}, text={text[:50]}...")

            # Step 1: AudioQuery
            t1 = time.monotonic()
            try:
                query_resp = await client.post(
                    f"{AIVISSPEECH_ENGINE_URL}/audio_query",
                    params={"speaker": style_id, "text": text},
                )
                if query_resp.status_code != 200:
                    detail = query_resp.text[:200]
                    logger.error(f"AudioQuery failed: {query_resp.status_code} - {detail}")
                    raise HTTPException(
                        status_code=502,
                        detail=f"AudioQuery failed: {detail}",
                    )
                audio_query = query_resp.json()
                logger.info(f"AudioQuery OK: {len(str(audio_query))} bytes JSON (took {time.monotonic() - t1:.1f}s)")
            except httpx.RequestError as e:
                logger.error(f"AudioQuery request error: {e}")
                raise HTTPException(
                    status_code=503,
                    detail=f"Cannot connect to AivisSpeech Engine: {e}",
                )

            # Step 2: Synthesis
            t2 = time.monotonic()
            try:
                synth_resp = await client.post(
                    f"{AIVISSPEECH_ENGINE_URL}/synthesis",
                    params={"speaker": style_id},
                    json=audio_query,
                )
                if synth_resp.status_code != 200:
                    detail = synth_resp.text[:200]
                    logger.error(f"Synthesis failed: {synth_resp.status_code} - {detail}")
                    raise HTTPException(
                        status_code=502,
                        detail=f"Synthesis failed: {detail}",
                    )
                wav_bytes = synth_resp.content
                logger.info(f"Synthesis OK: {len(wav_bytes)} bytes WAV (took {time.monotonic() - t2:.1f}s)")
            except httpx.RequestError as e:
                logger.error(f"Synthesis request error: {e}")
                raise HTTPException(
                    status_code=503,
                    detail=f"Cannot connect to AivisSpeech Engine: {e}",
                )

            logger.info(f"[TTS] Total time: {time.monotonic() - t_start:.1f}s, returning {len(wav_bytes)} bytes")
            return Response(
                content=wav_bytes,
                media_type="audio/wav",
                headers={"Content-Disposition": "inline; filename=tts_output.wav"},
            )


@app.get("/speakers")
async def list_speakers():
    """列出 AivisSpeech Engine 中所有可用话者"""
    async with httpx.AsyncClient(timeout=5.0, trust_env=False) as client:
        try:
            resp = await client.get(f"{AIVISSPEECH_ENGINE_URL}/speakers")
            resp.raise_for_status()
            return resp.json()
        except Exception as e:
            raise HTTPException(
                status_code=503,
                detail=f"Cannot fetch speakers: {e}",
            )


# ==================== 主入口 ====================

if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        app,
        host=BRIDGE_HOST,
        port=BRIDGE_PORT,
        log_level="info",
    )