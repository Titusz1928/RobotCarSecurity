import asyncio
import websockets
import socketio
import base64
import logging

# Setup logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("adapter")

# Socket.IO client (to main server)
sio = socketio.AsyncClient()

# ESP32 WebSocket client device_ids
connected_esp32_clients = {}

async def connect_to_socketio():
    await sio.connect('https://robotcar-server-368569625955.europe-central2.run.app')
    logger.info("[ADAPTER] Connected to main server")

# Handle esp32_command from main server
@sio.on('esp32_command')
async def handle_esp32_command(data):
    logger.info(f"[ADAPTER] Raw esp32_command data: {data}")

    device_id = data.get('device_id')
    cmd = data.get('message')

    logger.info(f"[ADAPTER] Received command for {device_id}: {cmd}")

    ws = connected_esp32_clients.get(device_id)
    if ws and ws.open:
        try:
            await ws.send(cmd)
            logger.info(f"[ADAPTER] Sent command to {device_id} over WS")
        except Exception as e:
            logger.error(f"[ADAPTER] Failed to send command to {device_id}: {e}")
    else:
        logger.warning(f"[ADAPTER] No WebSocket connection found for {device_id}")

# Send periodic ping to keep connection alive
async def ping_client(websocket, device_id):
    try:
        while True:
            await websocket.send("ping")
            await asyncio.sleep(30)
    except websockets.ConnectionClosed:
        logger.info(f"[ADAPTER] {device_id} disconnected during ping")

# Handle WebSocket from ESP32
async def handle_esp32(websocket, path):
    logger.info("[ADAPTER] ESP32 connected")

    try:
        first_message = await asyncio.wait_for(websocket.recv(), timeout=10)
    except asyncio.TimeoutError:
        logger.warning("[ADAPTER] No device ID received, closing connection")
        await websocket.close()
        return

    device_id_raw = first_message.strip()

    if device_id_raw.startswith("device_id:"):
        device_id = device_id_raw[len("device_id:"):].strip()
    else:
        device_id = device_id_raw

    logger.info(f"[ADAPTER] Parsed device ID: {device_id}")

    connected_esp32_clients[device_id] = websocket
    await sio.emit('esp32_connect', {'device_id': device_id})

    # Start ping task
    ping_task = asyncio.create_task(ping_client(websocket, device_id))

    try:
        async for message in websocket:
            logger.info(f"[ADAPTER] Received from {device_id}: {len(message)} bytes")

            if isinstance(message, bytes):
                image_b64 = base64.b64encode(message).decode('utf-8')
                await sio.emit('esp32_message', {
                    'device_id': device_id,
                    'type': 'image',
                    'message': image_b64
                })
            elif isinstance(message, str):
                if message.startswith("distance:"):
                    await sio.emit('esp32_message', {
                        'device_id': device_id,
                        'type': 'distance',
                        'message': message
                    })
                else:
                    await sio.emit('esp32_message', {
                        'device_id': device_id,
                        'type': 'text',
                        'message': message
                    })

    except websockets.ConnectionClosed:
        await sio.emit('esp32_message', {
            'device_id': device_id,
            'type': 'text',
            'message': "disconnected"
        })
        logger.warning(f"[ADAPTER] ESP32 {device_id} disconnected")

    except Exception as e:
        logger.error(f"[ADAPTER] Unexpected error handling websocket from {device_id}: {e}")

    finally:
        ping_task.cancel()
        if device_id in connected_esp32_clients:
            del connected_esp32_clients[device_id]

@sio.event
async def connect():
    logger.info("[ADAPTER] Socket.IO connected")

@sio.event
async def disconnect():
    logger.warning("[ADAPTER] Socket.IO disconnected")

async def main():
    logger.info("[ADAPTER] Starting WebSocket server...")
    ws_server = await websockets.serve(
        handle_esp32,
        "0.0.0.0",
        8080
    )
    logger.info("[ADAPTER] WebSocket server started on port 8080")

    try:
        await connect_to_socketio()
        logger.info("[ADAPTER] Connected to main server")
    except Exception as e:
        logger.error(f"[ADAPTER] Failed to connect to main server: {e}")

    await asyncio.Future()

if __name__ == '__main__':
    try:
        asyncio.run(main())
    except Exception as e:
        logger.critical(f"[ADAPTER] Unhandled exception in main(): {e}")
