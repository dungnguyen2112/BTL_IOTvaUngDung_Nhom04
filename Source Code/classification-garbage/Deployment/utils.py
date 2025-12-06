from tensorflow.keras.models import Sequential
from keras.layers import Conv2D, Flatten, MaxPooling2D, Dense, Dropout, SpatialDropout2D
from tensorflow.keras.losses import sparse_categorical_crossentropy, binary_crossentropy
from tensorflow.keras.optimizers import Adam
from tensorflow.keras.preprocessing.image import ImageDataGenerator
import numpy as np
from PIL import Image, ImageDraw, ImageFont



from tensorflow.keras.models import Sequential
from tensorflow.keras.layers import Conv2D, MaxPooling2D, Flatten, Dense, Dropout

import os
os.environ['TF_ENABLE_ONEDNN_OPTS'] = '1'

def gen_labels():
    # For the two-class waste_cnn.keras model
    # 0 -> Recyclable, 1 -> Organic (matches user's sample predict_func)
    return {0: "Recyclable", 1: "Organic"}

def preprocess(image):
    image = image.convert('RGB')  # ✅ ép ảnh thành 3 kênh
    image = image.resize((224, 224), Image.Resampling.LANCZOS)
    img_array = np.array(image)
    img_array = img_array / 255.0
    return img_array

def map_two_classes(label: str) -> str:
    # Map binary labels to Vietnamese categories
    # Organic -> rác hữu cơ, Recyclable -> rác vô cơ
    return "rác hữu cơ" if label.lower() == "organic" else "rác vô cơ"

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
