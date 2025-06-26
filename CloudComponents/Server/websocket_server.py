import base64
import os
import tempfile

from dotenv import load_dotenv
import threading
import logging

logging.basicConfig(
    level=logging.DEBUG,  # Change to INFO if you want less verbosity
    format='[%(asctime)s] %(levelname)s in %(module)s: %(message)s',
)

load_dotenv()

import firebase_admin
from firebase_admin import credentials, firestore
import lane_assist
from person_detector import detect_people
from image_uploader import upload_image_to_gcs
from flask import request
import json


#VARIABLES

# Global state
connected_devices = {}  # socketio session IDs
latest_frames = {}
latest_data = {}
firestore_device_settings = []

firebase_app = None
db = None

commands_message = (
    "Available commands:\n"
    " - help: Show this help message\n"
    " - clear: Clears terminal\n"
    " - Arduino commands:\n"
    "    Status=get Arduino info\n"
    "    Movement commands: forward, backward, right, left, stop\n"
    "    Assisted stop: ason=turn on, asoff=turn off\n"
    " - Esp32cam commands\n"
    "    Status: get ESP32 info = esp32_status\n"
    "    Set primary wifi = esp32_wifi:wifiname\n"
    "    Set primary wifi password = esp32_wifi:wifipassword\n"
    "    Activate lighting: esp32_led_on=turn on, esp32_led_off=turn off\n"
    "    Video Streaming: esp32_stream_on=turn on, esp32_stream_off=turn off\n"
    "    Change resolution: esp32_change_resolution:(value from 1-10, 1=lowest, 10=highest)\n"
    "    Measure Latency: ping"
)

#CLASSES
class DeviceSetting:
    def __init__(self, device_id: str, lane_assist: bool, person_detection: bool):
        self.device_id = device_id
        self.lane_assist = lane_assist
        self.person_detection = person_detection

    def __repr__(self):
        return f"Device(device_id='{self.device_id}', lane_assist={self.lane_assist}, person_detection={self.person_detection})"


#FUNCTIONS

def init_firebase(firebase_credentials_path_or_dict):
    global firebase_app, db

    if firebase_admin._apps:  # _apps is a dict of initialized apps
        firebase_app = list(firebase_admin._apps.values())[0]
        db = firestore.client(firebase_app)
        print("[websocket_server] Firebase already initialized")
        return

    if isinstance(firebase_credentials_path_or_dict, dict):
        cred = credentials.Certificate(firebase_credentials_path_or_dict)
    else:
        cred = credentials.Certificate(firebase_credentials_path_or_dict)

    firebase_app = firebase_admin.initialize_app(cred)
    db = firestore.client(firebase_app)
    print("[websocket_server] Firebase initialized")

def check_and_add_device(device_id: str):
    print(f"[check_and_add_device] Checking device: {device_id}")
    try:
        doc_ref = db.collection('devices').document(device_id)
        doc = doc_ref.get()
        if not doc.exists:
            print(f"[check_and_add_device] Device not found in Firestore, adding: {device_id}")
            doc_ref.set({
                'device_id': device_id,
                'personDetection': False,
                'laneAssist': False
            })
            load_devices_from_firestore()
        else:
            print(f"[check_and_add_device] Device {device_id} already exists in Firestore.")
    except Exception as e:
        print(f"[check_and_add_device] Error adding device {device_id}: {e}")

def load_devices_from_firestore():
    global firestore_device_settings
    print("[load_devices_from_firestore] Loading devices from Firestore")
    try:
        devices_ref = db.collection('devices')
        docs = devices_ref.stream()
        firestore_device_settings = []

        for doc in docs:
            data = doc.to_dict()
            device_setting = DeviceSetting(
                device_id=data.get("device_id", "unknown"),
                lane_assist=data.get("laneAssist", False),
                person_detection=data.get("personDetection", False)
            )
            firestore_device_settings.append(device_setting)

        print(f"[load_devices_from_firestore] Loaded {len(firestore_device_settings)} devices:")
        for d in firestore_device_settings:
            print("  ", d)

        return firestore_device_settings
    except Exception as e:
        print("[load_devices_from_firestore] Error loading devices:", e)
        return []


def get_connected_devices():
    print("[get_connected_devices] Returning connected devices")
    return connected_devices

#MAIN FUNCTION, CALLED IN MAIN.PY
def register_socketio_handlers(socketio):
    @socketio.on('esp32_connect')
    def handle_esp32_connect(data):
        print("[esp32_connect] Event received with data:", data)
        device_id = data.get('device_id', 'unknown')
        if device_id == 'unknown':
            print("[esp32_connect] Unknown device ID, ignoring")
            return

        connected_devices[device_id] = request.sid
        print(f"[esp32_connect] ESP32 connected: {device_id} (SID: {request.sid})")

        check_and_add_device(device_id)

        print("[esp32_connect] Emitting device_list_update and get_firestore_devices")
        socketio.emit('device_list_update', list(connected_devices.keys()))
        socketio.emit('get_firestore_devices')

    @socketio.on('esp32_message')
    def handle_esp32_message(data):
        device_id = data.get('device_id', 'unknown')
        if device_id == 'unknown':
            print("[esp32_message] Unknown device ID, ignoring message")
            return

        message_type = data.get('type', None)
        message = data.get('message')

        if message_type == 'image':
            if not message:
                print(f"[esp32_message] No image data received from {device_id}")
                return

            try:
                image_data = base64.b64decode(message)
                latest_frames[device_id] = image_data
                #print(f"[esp32_message] Received image  from {device_id}")
                socketio.emit('image_update', {'device_id': device_id, 'image': message})

                device_setting = next((d for d in firestore_device_settings if d.device_id == device_id), None)

                if device_setting:
                    #logging.debug(f"Attempting to detect people")
                    #  Person Detection
                    if device_setting.person_detection:
                        #logging.debug(f"Attempting to detect people")
                        #print(f"attempting to detect person for device {device_id}")
                        boxes, w, h = detect_people(image_data)
                        socketio.emit('people_detected', {
                            'device_id': device_id,
                            'count': len(boxes),
                            'boxes': boxes,
                            'w': w,
                            'h': h
                        }, broadcast=True)

                        if len(boxes) > 0:
                            #print("calling uploader function")
                            logging.debug(f"Attempting to upload")
                            upload_image_to_gcs(image_data)

                    # Lane Assist
                    if device_setting.lane_assist:
                        # print(f"[{device_id}] Lane Assisting...")
                        cmd = lane_assist.detect_lane_direction(image_data)
                        socketio.emit('lane_assist', {'direction': cmd}, broadcast=True)

                        # Determine opposite direction
                        if cmd in ['left', 'right']:
                            opposite_cmd = 'right' if cmd == 'left' else 'left'

                            # STOP DIRECTION AFTER 1 SECOND
                            def send_opposite():
                                socketio.emit('lane_assist', {'direction': opposite_cmd}, broadcast=True)

                            threading.Timer(1, send_opposite).start()



            except Exception as e:
                print(f"[esp32_message] Error decoding image data from {device_id}: {e}")

        elif message_type == 'text':
            print(f"[esp32_message] [{device_id}] Received text: {message}")
            socketio.emit('terminal_log', {'device_id': device_id, 'message': message})

        elif message_type == 'distance':
            try:
                #print(f"[esp32_message] Received text  from {device_id}")
                raw_value = float(
                    message.split(":")[1] if isinstance(message, str) and message.startswith("distance:") else message)
                formatted = f"{raw_value / 100:.2f} m" if raw_value > 100 else f"{int(raw_value)} cm"
                latest_data[device_id] = "distance:"+str(raw_value)
                socketio.emit('distance_update', {'device_id': device_id, 'distance': formatted})
                #print(f"[esp32_message] [{device_id}] Received distance: {formatted}")
            except Exception as e:
                print(f"[esp32_message] Invalid distance format from {device_id}: {e}")

        else:
            print(f"[esp32_message] Unhandled message type: {message_type}")

    @socketio.on('disconnect')
    def handle_disconnect():
        print(f"[disconnect] Client disconnected SID: {request.sid}")
        device_id = None
        for dev_id, sid in connected_devices.items():
            if sid == request.sid:
                device_id = dev_id
                break

        if device_id:
            print(f"[disconnect] ESP32 disconnected: {device_id}")
            connected_devices.pop(device_id, None)
            socketio.emit('esp32_disconnected', {'device_id': device_id})
            socketio.emit('device_list_update', list(connected_devices.keys()))
        else:
            print("[disconnect] Disconnected SID not found in connected devices")

    @socketio.on('esp32_command')
    def handle_send_to_esp32(data):
        print(f"[send_to_esp32] Data received: {data}")
        device_id = data.get('device_id')
        message = data.get('message')

        if not device_id or not message:
            print("[send_to_esp32] Missing device_id or message, ignoring")
            return

        msg_lower = message.strip().lower()

        if msg_lower == "help":
            print(f"[send_to_esp32] Sending help message to all clients")
            socketio.emit('terminal_log', {
                'device_id': device_id,
                'message': commands_message
            }, broadcast=True)
            return

        if msg_lower == "clear":
            print(f"[send_to_esp32] Sending clear command to all clients")
            socketio.emit('terminal_clear', broadcast=True)
            return

        # DEFAULT
        if device_id in connected_devices:
            print(f"[send_to_esp32] Sending command to device {device_id}")
            socketio.emit('esp32_command', {
                'device_id': device_id,
                'message': message
            }, room=connected_devices[device_id])
        else:
            print(f"[send_to_esp32] ESP32 {device_id} not connected")

    @socketio.on('reload_firestore_devices')
    def handle_reload_firestore_devices():
        print("[reload_firestore_devices] Reloading Firestore devices")
        load_devices_from_firestore()
        socketio.emit('firestore_devices_updated')

#INITIALIZING

# Initialize firestore devices
firebase_credentials_content = os.getenv("FIREBASE_DATABASE_CREDENTIALS", "")

if firebase_credentials_content.strip().startswith("{"):
    try:
        # Parse JSON content
        credentials_dict = json.loads(firebase_credentials_content)
        init_firebase(credentials_dict)
    except json.JSONDecodeError:
        # Fallback to writing temp file
        with tempfile.NamedTemporaryFile(mode='w', suffix='.json', delete=False) as temp_file:
            temp_file.write(firebase_credentials_content)
            init_firebase(temp_file.name)
else:
    init_firebase(firebase_credentials_content)

load_devices_from_firestore()




