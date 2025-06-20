import threading
import asyncio
import io

from flask import Flask, render_template, request, redirect, url_for, jsonify, send_file, abort, session, flash
from flask_socketio import SocketIO
from gevent.testing import sockets

from google.cloud import storage

from datetime import timedelta, datetime

from google.oauth2 import service_account

import warnings

from passwordoperations import get_stored_password
from werkzeug.security import check_password_hash
from functools import wraps
import os
import websocket_server
import logging
import json
import tempfile


#DEVELOPMENT MODE
#from dotenv import load_dotenv
#load_dotenv(override=True)


warnings.filterwarnings("ignore", category=FutureWarning)

# logging.basicConfig(level=logging.DEBUG)



#INITIALIZING APP
app = Flask(__name__)
app.config['SECRET_KEY'] = os.getenv("FLASK_SECRET_KEY")
socketio = SocketIO(app,
                   cors_allowed_origins="*",
                   async_mode='eventlet',
                   logger=False,
                   engineio_logger=False)


#INITIALIZING FIREBASE
firebase_credentials_content = os.environ.get("FIREBASE_DATABASE_CREDENTIALS", "")

if firebase_credentials_content.strip().startswith("{"):
    temp_firebase_file = tempfile.NamedTemporaryFile(delete=False, suffix=".json", mode="w")
    temp_firebase_file.write(firebase_credentials_content)
    temp_firebase_file.close()
    firebase_credentials_path = temp_firebase_file.name
else:
    firebase_credentials_path = firebase_credentials_content  # Assume it's a path

#INITIALIZING GOOGLE CLOUD
google_credentials_content = os.environ.get("GOOGLE_APPLICATION_CREDENTIALS", "")

if google_credentials_content.strip().startswith("{"):
    temp_credentials_file = tempfile.NamedTemporaryFile(delete=False, suffix=".json", mode="w")
    temp_credentials_file.write(google_credentials_content)
    temp_credentials_file.close()
    google_credentials_path = temp_credentials_file.name
    os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = google_credentials_path
else:
    google_credentials_path = google_credentials_content

credentials = service_account.Credentials.from_service_account_file(google_credentials_path)
storage_client = storage.Client(credentials=credentials)

PROCESSED_BUCKET = os.environ["PROCESSED_BUCKET"]
PREPROCESSED_BUCKET = os.environ["PREPROCESSED_BUCKET"]


try:
    import json
    cred_dict = json.loads(firebase_credentials_content)
except Exception:
    cred_dict = None

if not cred_dict:
    websocket_server.init_firebase(firebase_credentials_path)


from websocket_server import register_socketio_handlers

register_socketio_handlers(socketio)


api_key=os.getenv('API_KEY')


commands_message = (
    "Available commands:\n"
    " - help: Show this help message\n"
    " - clear: Clears terminal\n"
    " - Arduino commands:\n"
    "    Status=get Arduino info\n"
    "    Movement commands: forward, backward, right, left, stop\n"
    "    Assisted stop: ason=turn on, asoff=turn off\n"
    " - Esp32cam commands\n"
    "    Activate lighting: esp32_led_on=turn on, esp32_led_off=turn off\n"
    "    Video Streaming: esp32_stream_on=turn on, esp32_stream_off=turn off\n"
    "    Change resolution: esp32_change_resolution:(value from 1-10, 1=lowest, 10=highest)\n"
    "    Measure Latency: ping"
)


#FLASK SERVER
def api_key_required(f):
    @wraps(f)
    def decorated_function(*args, **kwargs):
        received_api_key = request.headers.get('X-API-KEY') or request.args.get('api_key')
        if api_key != received_api_key:
            return jsonify({'error': 'Invalid API key'}), 401
        return f(*args, **kwargs)
    return decorated_function


@app.before_request
def require_authentication():
    allowed_routes = ['login', 'static']
    api_routes = ['list_devices', 'current_frame', 'current_data', 'send_to_esp32', 'api_detections']

    # ALLOWED ROUTES
    if request.endpoint in allowed_routes:
        return

    # SKIP AUTH CHECK FOR API ROUTES (PROTECTED BY API KEY)
    if request.endpoint in api_routes:
        return

    # AUTH CHECK FOR ALL OTHER ROUTES (WEB)
    if not session.get('authenticated'):
        return redirect(url_for('login'))

@app.route('/login', methods=['GET', 'POST'])
def login():
    if request.method == 'POST':
        password = request.form.get('password')
        if check_password_hash(get_stored_password(), password):
            session['authenticated'] = True
            return redirect(url_for('home'))
        else:
            return render_template('login/index.html',
                                   error="Invalid password")
    return render_template('login/index.html')

@app.route('/logout')
def logout():
    session.pop('authenticated', None)
    return redirect(url_for('login'))

@app.route('/')
def default():
    return redirect('/home')

@app.route('/home')
def home():
    return render_template('home/index.html')

@app.route('/control')
def control():
    device_id = request.args.get('device_id')

    if device_id:
        # DEVICE SELECTED
        return render_template('controler/index.html', device_id=device_id)
    else:
        # NO DEVICE SELECTED
        return render_template('controler/index.html')

@app.route('/all-devices')
def all_devices():
    return render_template('alldevices/index.html')

@app.route('/detections')
def detections():
    bucket = storage_client.bucket(PROCESSED_BUCKET)
    blobs = list(bucket.list_blobs())

    blobs = sorted(blobs, key=lambda b: b.time_created or datetime.min, reverse=True)[:20]

    images = []
    for blob in blobs:
        if blob.name.lower().endswith(('.jpg', '.jpeg', '.png')):
            signed_url = blob.generate_signed_url(
                version="v4",
                expiration=timedelta(minutes=30)
            )
            images.append({
                "url": signed_url,
                "name": blob.name,
                "display_name": blob.name.split("/")[-1],
                "created": blob.time_created
            })


    if request.args.get('format') == 'json':
        return jsonify(images)

    return render_template("detections/index.html", images=images)

@app.route('/api/detections')
@api_key_required
def api_detections():
    bucket = storage_client.bucket(PROCESSED_BUCKET)
    blobs = list(bucket.list_blobs())
    blobs = [b for b in blobs if b.name.lower().endswith(('.jpg', '.jpeg', '.png'))]

    # SORT BLOBS
    blobs = sorted(blobs, key=lambda b: b.time_created or datetime.min, reverse=True)

    # HANDEL QUERY PARAMS
    start = int(request.args.get('start', 0))
    count = request.args.get('count')

    if count is not None:
        try:
            count = int(count)
        except ValueError:
            return jsonify({"error": "Invalid 'count' parameter"}), 400
        blobs = blobs[start:start + count]
    else:
        blobs = blobs[start:]

    images = []
    for blob in blobs:
        signed_url = blob.generate_signed_url(
            version="v4",
            expiration=timedelta(minutes=30))
        images.append({
            "url": signed_url,
            "name": blob.name,
            "display_name": blob.name.split("/")[-1],
            "created": blob.time_created
        })

    return jsonify(images)



@app.route('/delete-image', methods=['DELETE'])
def delete_image():
    filename = request.args.get('name')
    if not filename:
        return jsonify({'error': 'Filename is required'}), 400

    bucket = storage.Client().bucket(PROCESSED_BUCKET)
    blob = bucket.blob(filename)
    if blob.exists():
        blob.delete()
        return jsonify({'message': 'Image deleted'}), 200
    else:
        return jsonify({'error': 'File not found'}), 404

@app.route('/rename-image', methods=['POST'])
def rename_image():
    data = request.get_json()
    old_name = data.get('old_name')
    new_name = data.get('new_name')

    if not old_name or not new_name:
        return jsonify({'error': 'Missing parameters'}), 400

    bucket = storage.Client().bucket(PROCESSED_BUCKET)
    source_blob = bucket.blob(old_name)

    if not source_blob.exists():
        return jsonify({'error': 'Source file not found'}), 404

    prefix = '/'.join(old_name.split('/')[:-1])
    if prefix:
        new_name = f"{prefix}/{new_name}"

    new_blob = bucket.copy_blob(source_blob, bucket, new_name)
    source_blob.delete()

    return jsonify({'message': 'Image renamed'}), 200




@app.route('/send', methods=['POST'])
@api_key_required
def send_to_esp32():
    msg = request.form['message']
    device_id = request.form.get('device_id')
    #print("sending command")
    print(device_id+" "+msg)

    if msg.strip().lower() == "help":
        if socketio:
            socketio.emit('terminal_log', {
                'device_id': device_id,
                'message': commands_message
            }, broadcast=True)
            return redirect(url_for('control'))
        else:
            return "SocketIO not initialized", 500

    if msg.strip().lower() == "clear":
        if(socketio):
            socketio.emit('terminal_clear',broadcast=True)
            return redirect(url_for('control'))
        else:
            return "SocketIO not initialized",500

    # DEFAULT CASE
    if socketio:
        socketio.emit('esp32_command', {
            'device_id': device_id,
            'message': msg
        }, room=websocket_server.connected_devices.get(device_id))
        return "Command sent", 200
    else:
        return "SocketIO not initialized", 500


@app.route('/devices')
@api_key_required
def list_devices():
    return jsonify(list(websocket_server.connected_devices.keys()))

@app.route('/current_frame')
@api_key_required
def current_frame():
    device_id = request.args.get('device_id')
    if not device_id:
        return "Missing device_id parameter", 400

    frame = websocket_server.latest_frames.get(device_id)

    if frame is None:
        return f"No image yet for device {device_id}", 404

    return send_file(
        io.BytesIO(frame),
        mimetype='image/jpeg',
        as_attachment=False,
        download_name=f'{device_id}_frame.jpg'
    )

@app.route('/current_data')
@api_key_required
def current_data():
    device_id = request.args.get('device_id')
    if not device_id:
        return "Missing device_id parameter", 400

    data = websocket_server.latest_data.get(device_id)

    if data is None:
        return f"No data yet for device {device_id}", 404

    return jsonify({'data': data}), 200

@app.errorhandler(404)
def page_not_found(e):
    return render_template('notfound/index.html')

@socketio.on('connect')
def on_browser_connect():
    print("Browser connected")


#FOR DEVELOPMENT
if __name__ == '__main__':
    port = int(os.environ.get('PORT', 4000))
    logging.debug(f"Starting Flask app with SocketIO on port {port}")
    socketio.run(app, host='0.0.0.0', port=port)
else:
    print(f"socketio was not started: {os.environ.get('PORT')}")
    application = app


