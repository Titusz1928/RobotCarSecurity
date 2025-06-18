import asyncio
import websockets
import random
import time
import base64
import cv2
import numpy as np

DEVICE_ID = "SIM04"
WEBSOCKET_SERVER = "ws://:8765"

# OpenCV VideoCapture initialization
cap = cv2.VideoCapture(0)  # 0 = default webcam

if not cap.isOpened():
    raise Exception("Webcam (or DroidCam) not found. Make sure it's active and accessible.")


# Capture a frame from the webcam and encode as JPEG
def get_webcam_image_bytes():
    ret, frame = cap.read()
    if not ret:
        print("Failed to capture frame")
        return b""

    # Resize for consistency if needed
    frame = cv2.resize(frame, (320, 240))

    # Encode the frame as JPEG
    success, buffer = cv2.imencode('.jpg', frame)
    if success:
        return buffer.tobytes()
    else:
        return b""


# Simulate a distance sensor reading
def get_fake_distance():
    return f"distance:{random.randint(50, 300)}"


async def simulate_device():
    async with websockets.connect(WEBSOCKET_SERVER) as websocket:
        print(f"[{DEVICE_ID}] Connected to server")

        # Send identification
        await websocket.send(f"device_id:{DEVICE_ID}")

        while True:
            # Send webcam image
            image_data = get_webcam_image_bytes()
            if image_data:
                await websocket.send(image_data)
                print(f"[{DEVICE_ID}] Sent image ({len(image_data)} bytes)")

            # Occasionally send a distance reading
            if random.random() < 0.3:
                dist = get_fake_distance()
                await websocket.send(dist)
                print(f"[{DEVICE_ID}] Sent sensor data: {dist}")

            # Handle incoming text messages
            try:
                message = await asyncio.wait_for(websocket.recv(), timeout=0.1)
                if isinstance(message, str):
                    print(f"[{DEVICE_ID}] Received command: {message}")
            except asyncio.TimeoutError:
                pass

            await asyncio.sleep(0.5)


try:
    asyncio.run(simulate_device())
finally:
    cap.release()
