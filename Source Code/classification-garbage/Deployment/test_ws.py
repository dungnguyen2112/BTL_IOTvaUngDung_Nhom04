import asyncio
import base64
import json
from datetime import datetime, timezone
from io import BytesIO

import numpy as np
from PIL import Image
import websockets

from utils import (
    preprocess,
    model_arc,
    gen_labels,
    annotate_image,
    map_two_classes,
)


# ===== Global model and labels (loaded once) =====
MODEL = None
LABELS = None


def _utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def _infer_pil_format_from_content_type(content_type: str) -> str:
    ct = (content_type or "").lower()
    if "png" in ct:
        return "PNG"
    # default to JPEG if not specified
    return "JPEG"


def _classify_pil_image(pil_image: Image.Image) -> tuple[str, str]:
    """
    Returns (predicted_label, binary_label)
    """
    img_arr = preprocess(pil_image)
    preds = MODEL.predict(img_arr[np.newaxis, ...])
    idx = int(np.argmax(preds[0], axis=-1))
    pred_label = LABELS.get(idx, str(idx))
    binary_label = map_two_classes(pred_label)
    return pred_label, binary_label


async def handle(websocket, path):
    print(f"Client connected path={path}")

    # Enforce path to match /ws (so it aligns with wss://.../ws)
    if path != "/ws":
        try:
            await websocket.send(
                json.dumps(
                    {
                        "type": "server:error",
                        "payload": {"message": f"Invalid path: {path}. Use /ws"},
                    }
                )
            )
        finally:
            await websocket.close(code=1008, reason="Invalid path")
        return

    # Optional hello
    await websocket.send(
        json.dumps(
            {
                "type": "server:hello",
                "payload": {"message": "Hello from Python WebSocket server"},
            }
        )
    )

    async for raw in websocket:
        try:
            msg = json.loads(raw)
        except Exception:
            print("Non-JSON message:", raw)
            continue

        msg_type = msg.get("type")
        payload = msg.get("payload") or {}

        if msg_type == "esp32:ping":
            await websocket.send(
                json.dumps(
                    {
                        "type": "server:pong",
                        "payload": {"receivedAt": _utc_now_iso()},
                    }
                )
            )
            continue

        if msg_type == "esp32:image":
            b64_data = payload.get("data")
            filename = payload.get("filename") or "image.jpg"
            content_type = payload.get("contentType") or "image/jpeg"

            if not b64_data:
                await websocket.send(
                    json.dumps(
                        {
                            "type": "server:error",
                            "payload": {
                                "message": "Missing payload.data (base64 image)"
                            },
                        }
                    )
                )
                continue

            try:
                image_bytes = base64.b64decode(b64_data, validate=True)
                pil_img = Image.open(BytesIO(image_bytes))
            except Exception as e:
                await websocket.send(
                    json.dumps(
                        {
                            "type": "server:error",
                            "payload": {"message": f"Invalid image data: {e}"},
                        }
                    )
                )
                continue

            try:
                print("tien xu ly")
                pred_label, binary_label = _classify_pil_image(pil_img)
                print(binary_label)
                annotated = annotate_image(
                    pil_img, f"{pred_label} ({binary_label})"
                )

                # Re-encode with original content type hint
                fmt = _infer_pil_format_from_content_type(content_type)
                buf = BytesIO()
                annotated.save(buf, format=fmt, quality=90)
                buf.seek(0)
                out_b64 = base64.b64encode(buf.getvalue()).decode("ascii")

                response = {
                    "type": "server:image",
                    "payload": {
                        "filename": f"pred-{filename}",
                        "contentType": content_type,
                        "data": out_b64,
                        "receivedAt": _utc_now_iso(),
                        # Optional extra metadata:
                        "prediction": {
                            "label": pred_label,
                            "binary": binary_label,
                        },
                    },
                }
                await websocket.send(json.dumps(response))

                print(f"Processed image '{filename}': {pred_label} -> {binary_label}")
            except Exception as e:
                await websocket.send(
                    json.dumps(
                        {
                            "type": "server:error",
                            "payload": {"message": f"Processing failed: {e}"},
                        }
                    )
                )

            continue

        # Unknown message type
        await websocket.send(
            json.dumps(
                {
                    "type": "server:error",
                    "payload": {"message": f"Unknown message type: {msg_type}"},
                }
            )
        )


async def main():
    global MODEL, LABELS

    # Load once at startup
    print("Loading labels and model...")
    LABELS = gen_labels()
    MODEL = model_arc()
    MODEL.load_weights("./weights/modelnew.h5")
    print("Model loaded.")

    async with websockets.serve(handle, "0.0.0.0", 8765):
        print("WebSocket server running on ws://0.0.0.0:8765/ws")
        await asyncio.Future()  # keep server alive forever


if __name__ == "__main__":
    asyncio.run(main())
