from tensorflow.keras.models import Sequential
from keras.layers import Conv2D, Flatten, MaxPooling2D, Dense, Dropout, SpatialDropout2D
from tensorflow.keras.losses import sparse_categorical_crossentropy, binary_crossentropy
from tensorflow.keras.optimizers import Adam
from tensorflow.keras.preprocessing.image import ImageDataGenerator
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from typing import Tuple



from tensorflow.keras.models import Sequential
from tensorflow.keras.layers import Conv2D, MaxPooling2D, Flatten, Dense, Dropout

import os
os.environ['TF_ENABLE_ONEDNN_OPTS'] = '1'

def gen_labels():
    train = './Data/Train'
    # If the training directory is not available at runtime, return empty mapping
    if not os.path.isdir(train):
        return {}
    train_generator = ImageDataGenerator(rescale = 1/255)
    train_generator = train_generator.flow_from_directory(
        train,
        target_size=(300, 300),
        batch_size=32,
        class_mode='sparse'
    )
    labels = train_generator.class_indices
    # Invert to map index -> class_name
    return dict((v, k) for k, v in labels.items())

def preprocess(image, target_size=(300, 300)):
    image = image.convert('RGB')  # ép ảnh thành 3 kênh
    image = image.resize(target_size, Image.Resampling.LANCZOS)
    img_array = np.array(image).astype(np.float32) / 255.0
    return img_array

def map_two_classes(label: str) -> str:
    # Map 6-class → 2-class (trash → rác hữu cơ, others → rác vô cơ)
    return "rác hữu cơ" if label == "trash" else "rác vô cơ"

def annotate_image(image: Image.Image, text: str) -> Image.Image:
    """
    Draw a semi-transparent label box with text onto the image.
    Keeps the original format if possible. Returns a new PIL Image.
    """
    # Ensure we have an editable mode with alpha channel
    if image.mode != "RGBA":
        base = image.convert("RGBA")
    else:
        base = image.copy()

    overlay = Image.new("RGBA", base.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)

    try:
        font = ImageFont.load_default()
    except Exception:
        font = None

    padding_x = 12
    padding_y = 8
    margin = 12

    # Measure text
    if font is not None:
        text_w, text_h = draw.textsize(text, font=font)
    else:
        text_w, text_h = draw.textsize(text)

    box_w = text_w + padding_x * 2
    box_h = text_h + padding_y * 2

    # Top-left corner
    x0 = margin
    y0 = margin
    x1 = x0 + box_w
    y1 = y0 + box_h

    # Semi-transparent black box
    draw.rectangle([(x0, y0), (x1, y1)], fill=(0, 0, 0, 140))

    # White text
    text_x = x0 + padding_x
    text_y = y0 + padding_y
    if font is not None:
        draw.text((text_x, text_y), text, fill=(255, 255, 255, 255), font=font)
    else:
        draw.text((text_x, text_y), text, fill=(255, 255, 255, 255))

    # Composite overlay onto base image
    out = Image.alpha_composite(base, overlay)

    # Return in RGB to be safer when saving to JPEG
    return out.convert("RGB")

def model_arc():
    model = Sequential()

    # Convolution blocks
    model.add(Conv2D(32, kernel_size=(3,3), padding='same', input_shape=(300,300,3), activation='relu'))
    model.add(MaxPooling2D(pool_size=2))

    model.add(Conv2D(64, kernel_size=(3,3), padding='same', activation='relu'))
    model.add(MaxPooling2D(pool_size=2))

    model.add(Conv2D(32, kernel_size=(3,3), padding='same', activation='relu'))
    model.add(MaxPooling2D(pool_size=2))

    # Classification layers
    model.add(Flatten())

    model.add(Dense(64, activation='relu'))
    model.add(Dropout(0.2))
    model.add(Dense(32, activation='relu'))

    model.add(Dropout(0.2))
    model.add(Dense(6, activation='softmax'))

    # Enable OneDNN optimizations
    model.compile(optimizer='adam', loss='sparse_categorical_crossentropy', metrics=['accuracy'])
    

    return model

def predict_bgr_binary(img_bgr: np.ndarray, model, show: bool = False) -> Tuple[str, float, str]:
    """
    Predict using an OpenCV BGR image with a binary Keras model (.keras).
    Returns (pred_label_en, confidence, binary_label_vi).
    """
    # Determine input size from model if possible
    try:
        height = int(model.input_shape[1])
        width = int(model.input_shape[2])
        target_size = (width, height)
    except Exception:
        target_size = (300, 300)

    # Convert BGR (cv2) -> RGB and preprocess consistently with pipeline
    img_rgb = img_bgr[..., ::-1]
    pil_img = Image.fromarray(img_rgb)
    img_arr = preprocess(pil_img, target_size=target_size)  # float32 [0,1], HxWx3

    # Predict
    preds = model.predict(img_arr[np.newaxis, ...])  # (1, C) or (1, 1)


    # Handle sigmoid (1 unit) vs softmax (2 units)
    if preds.shape[-1] == 1:
        prob_organic = float(preds[0][0])
        idx = 1 if prob_organic >= 0.5 else 0
        confidence = prob_organic if idx == 1 else (1.0 - prob_organic)
    else:
        idx = int(np.argmax(preds[0], axis=-1))
        confidence = float(np.max(preds[0]))
    print("confidence:", confidence)
    print("preds:", preds[0])
    # Binary mapping consistent with ws_client
    pred_label = "Organic" if idx == 0 else "Recyclable"
    binary_label = "rác hữu cơ" if idx == 0 else "rác vô cơ"

    # Optional visualize (no matplotlib dependency; use PIL show)
    if show:
        try:
            pil_img.show()
        except Exception:
            pass

    return pred_label, confidence, binary_label
