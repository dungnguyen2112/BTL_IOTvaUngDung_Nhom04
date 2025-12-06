import asyncio
import base64
import json
import ssl
from datetime import datetime, timezone
from io import BytesIO
from typing import Tuple

import numpy as np
from PIL import Image
import websockets

from utils import (
    preprocess,
    gen_labels,
    annotate_image,
    map_two_classes,
)


DEFAULT_WS_URL = "wss://ntdung.systems/ws"

# Globals loaded once
MODEL = None
LABELS = None


def _utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def _infer_pil_format_from_content_type(content_type: str) -> str:
    ct = (content_type or "").lower()
    if "png" in ct:
        return "PNG"
    return "JPEG"


def _classify_pil_image(pil_image: Image.Image) -> Tuple[str, str]:
    """
    Returns (predicted_label, binary_label)
    """
    img_arr = preprocess(pil_image)
    preds = MODEL.predict(img_arr[np.newaxis, ...])
    idx = int(np.argmax(preds[0], axis=-1))
    pred_label = LABELS.get(idx, str(idx))
    binary_label = map_two_classes(pred_label)
    return pred_label, binary_label


async def process_incoming_message(ws, message: str):
    print(message)
    try:
        msg = json.loads(message)
    except Exception:
        # Ignore non-JSON messages
        return

    msg_type = msg.get("type")
    payload = msg.get("payload") or {}

    if msg_type == "server:ping":
        await ws.send(
            json.dumps({"type": "client:pong", "payload": {"receivedAt": _utc_now_iso()}})
        )
        return

    if msg_type == "server:image":
        b64_data = payload.get("data")

        if not b64_data:
            await ws.send(
                json.dumps(
                    {
                        "type": "server:error",
                        "payload": {"message": "Missing payload.data (base64 image)"},
                    }
                )
            )
            return

        try:
            image_bytes = base64.b64decode(b64_data, validate=True)
            pil_img = Image.open(BytesIO(image_bytes))
        except Exception as e:
            await ws.send(
                json.dumps(
                    {
                        "type": "server:error",
                        "payload": {"message": f"Invalid image data: {e}"},
                    }
                )
            )
            return

        try:
            print("tien xu ly")
            pred_label, binary_label = _classify_pil_image(pil_img)
            print(binary_label)
            # Map to motion command
            command = "ROTATE_CCW" if binary_label == "rác hữu cơ" else "ROTATE_CW"
            control_msg = {
                "type": "esp32:data",
                "payload": command,
            }
            print(control_msg)
            await ws.send(json.dumps(control_msg))
            return
        except Exception as e:
            await ws.send(
                json.dumps(
                    {
                        "type": "server:error",
                        "payload": {"message": f"Processing failed: {e}"},
                    }
                )
            )
            return

    # Unknown message type: ignore or log
    return


async def run_client(url: str = DEFAULT_WS_URL):
    print(f"Connecting to {url} ...")
    ssl_context = None
    if url.startswith("wss://"):
        ssl_context = ssl.create_default_context()

    backoff = 1.0
    while True:
        try:
            async with websockets.connect(url, ssl=ssl_context) as ws:
                print("Connected.")
                # Identify ourselves (optional)
                await ws.send(
                    json.dumps(
                        {
                            "type": "client:hello",
                            "payload": {"message": "Python AI client connected"},
                        }
                    )
                )
                backoff = 1.0
                async for message in ws:
                    await process_incoming_message(ws, message)
        except Exception as e:
            print(f"Disconnected/error: {e}. Reconnecting in {backoff:.1f}s ...")
            await asyncio.sleep(backoff)
            backoff = min(backoff * 2.0, 30.0)


async def main():
    global MODEL, LABELS
    print("Loading labels and model ...")
    LABELS = gen_labels()
    # Load full Keras model saved in .keras format
    from tensorflow.keras.models import load_model
    MODEL = load_model("./weights/waste_cnn.keras")
    print("Model loaded.")

    await run_client(DEFAULT_WS_URL)


if __name__ == "__main__":
    asyncio.run(main())


