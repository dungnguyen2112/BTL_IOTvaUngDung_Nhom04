import time
import os
from datetime import datetime
import requests
import streamlit as st
import numpy as np
from PIL import Image
import urllib.request
from utils import *

labels = gen_labels()

# Map 6-class → 2-class (trash → rác hữu cơ, others → rác vô cơ)
def map_two_classes(label: str) -> str:
    return "rác hữu cơ" if label == "trash" else "rác vô cơ"

# ===== ESP32-CAM capture config =====
ESP32_IP = "192.168.80.141"
CAPTURE_URL = f"http://{ESP32_IP}/capture"
SAVE_DIR = "images"
os.makedirs(SAVE_DIR, exist_ok=True)


def capture_image_from_esp32():
    try:
        response = requests.get(CAPTURE_URL, timeout=10)
        if response.status_code == 200:
            filename = datetime.now().strftime("%Y%m%d_%H%M%S.jpg")
            filepath = os.path.join(SAVE_DIR, filename)
            with open(filepath, "wb") as f:
                f.write(response.content)
            return filepath
        else:
            raise RuntimeError(f"ESP32-CAM HTTP {response.status_code}")
    except requests.exceptions.RequestException as err:
        raise RuntimeError(f"Cannot connect ESP32-CAM: {err}")

html_temp = '''
  <div style="display: flex; flex-direction: column; align-items: center; justify-content: center; margin-top: -50px">
    <div style = "display: flex; flex-direction: row; align-items: center; justify-content: center;">
     <center><h1 style="color: #000; font-size: 50px;"><span style="color: #0e7d73">Smart </span>Garbage</h1></center>
    <img src="https://cdn-icons-png.flaticon.com/128/1345/1345823.png" style="width: 0px;">
    </div>
    <div style="margin-top: -20px">
    <img src="https://i.postimg.cc/W3Lx45QB/Waste-management-pana.png" style="width: 400px;">
    </div>  
    </div>
    '''

st.markdown(html_temp, unsafe_allow_html=True)
html_temp = '''
    <div>
    <center><h3 style="color: #008080; margin-top: -20px">Check the type here </h3></center>
    </div>
    '''
# st.set_option('deprecation.showfileUploaderEncoding', False)
st.markdown(html_temp, unsafe_allow_html=True)
opt = st.selectbox(
    "How do you want to upload the image for classification?\n",
    ('Please Select', 'Upload image via link', 'Upload image from device', 'Capture from ESP32-CAM')
)

# Keep state for current image and whether we've handled prediction already (capture flow)
image = None
handled_capture_flow = False

if opt == 'Upload image from device':
    file = st.file_uploader('Select', type=['jpg', 'png', 'jpeg'])
    if file is not None:
        image = Image.open(file)

elif opt == 'Upload image via link':
    try:
        img = st.text_input('Enter the Image Address')
        image = Image.open(urllib.request.urlopen(img)) if img else None
    except:
        if st.button('Submit'):
            show = st.error("Please Enter a valid Image Address!")
            time.sleep(4)
            show.empty()

elif opt == 'Capture from ESP32-CAM':
    if st.button('Capture & Predict'):
        with st.spinner('Capturing from ESP32-CAM...'):
            try:
                captured_path = capture_image_from_esp32()
                image = Image.open(captured_path)
                st.image(image, width=300, caption='Captured Image')
                # Run prediction immediately after capture
                preprocessed = preprocess(image)
                model = model_arc()
                model.load_weights("./weights/modelnew.h5")
                prediction = model.predict(preprocessed[np.newaxis, ...])
                pred_class = labels[np.argmax(prediction[0], axis=-1)]
                binary_label = map_two_classes(pred_class)
                st.info(f'Kết quả: {binary_label}')
                handled_capture_flow = True
            except Exception as e:
                st.error(str(e))

# Default predict flow for upload/link
if not handled_capture_flow:
    try:
        if image is not None:
            st.image(image, width=300, caption='Uploaded Image')
            if st.button('Predict'):
                img_arr = preprocess(image)
                model = model_arc()
                model.load_weights("./weights/modelnew.h5")
                prediction = model.predict(img_arr[np.newaxis, ...])
                pred_class = labels[np.argmax(prediction[0], axis=-1)]
                binary_label = map_two_classes(pred_class)
                st.info(f'Kết quả: {binary_label}')
    except Exception as e:
        st.info(e)
        pass
