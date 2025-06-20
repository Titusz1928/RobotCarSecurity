import asyncio
import websockets
import random
import time
import base64
import cv2
import numpy as np

DEVICE_ID = "SIM04"
WEBSOCKET_SERVER = "wss://"

# OpenCV VideoCapture initialization
cap = cv2.VideoCapture(0)  # 0 = default webcam, DroidCam usually appears as 1 or 2

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
    while True:
        try:
            async with websockets.connect(WEBSOCKET_SERVER) as websocket:
                print(f"[{DEVICE_ID}] Connected to server")
                await websocket.send(f"device_id:{DEVICE_ID}")

                while True:
                    image_data = get_webcam_image_bytes()
                    await websocket.send(image_data)
                    print(f"[{DEVICE_ID}] Sent image ({len(image_data)} bytes)")

                    if random.random() < 0.3:
                        dist = get_fake_distance()
                        await websocket.send(dist)
                        print(f"[{DEVICE_ID}] Sent sensor data: {dist}")

                    try:
                        message = await asyncio.wait_for(websocket.recv(), timeout=0.1)
                        if isinstance(message, str):
                            msg_lower = message.strip().lower()
                            if msg_lower == "ping":
                                await websocket.send("pong")
                                print(f"[{DEVICE_ID}] Responded with pong")
                            elif msg_lower == "automaticping":
                                await websocket.send("automaticpong")
                                print(f"[{DEVICE_ID}] Responded with pong")
                            else:
                                print(f"[{DEVICE_ID}] Received command: {message}")
                    except asyncio.TimeoutError:
                        pass  # No message this cycle

                    await asyncio.sleep(0.5)

        except (websockets.exceptions.ConnectionClosedError,
                websockets.exceptions.InvalidStatusCode,
                ConnectionRefusedError,
                OSError) as e:
            print(f"[{DEVICE_ID}] Connection lost or failed: {e}. Reconnecting in 5 seconds...")
            await asyncio.sleep(5)  # Wait before reconnecting
        except Exception as e:
            print(f"[{DEVICE_ID}] Unexpected error: {e}. Reconnecting in 5 seconds...")
            await asyncio.sleep(5)


try:
    asyncio.run(simulate_device())
finally:
    cap.release()
