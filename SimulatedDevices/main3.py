import asyncio
import websockets
import random
import time
import base64
import cv2
import numpy as np

DEVICE_ID = "SIM03"  # Unique identifier for this simulated device
WEBSOCKET_SERVER = "wss://"


# Load test images only once
test_images = [
    cv2.imread("testimage1.jpg"),
    cv2.imread("testimage2.jpg"),
    cv2.imread("testimage3.jpg")
]

# Simulate a binary image (normally JPEG data)
def get_fake_image_bytes():
    # Choose one test image randomly with specified weights
    img = random.choices(test_images, weights=[49.5, 49.5, 1], k=1)[0]

    if img is None:
        print("Error: Test image not found or failed to load.")
        return b""

    # Resize to match ESP32-CAM resolution if needed
    img = cv2.resize(img, (320, 240))

    # Encode as JPEG
    success, buffer = cv2.imencode('.jpg', img)
    return buffer.tobytes() if success else b""


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
                    image_data = get_fake_image_bytes()
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

# Run the simulation
asyncio.run(simulate_device())
