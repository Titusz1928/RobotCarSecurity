import base64
import json
import logging
import firebase_admin
from firebase_admin import credentials, messaging

logging.basicConfig(level=logging.INFO)

# Firebase setup
cred = credentials.Certificate("securityrobotcar-firebase-adminsdk-fbsvc-4ae6d7e925.json")
firebase_admin.initialize_app(cred)


def send_push_notification(blob_name: str):
    message = messaging.Message(
        notification=messaging.Notification(
            title="New Detection",
            body=f"{blob_name} uploaded to cloud"
        ),
        topic="detections"
    )
    response = messaging.send(message)
    logging.info(f"FCM message sent: {response}")


def pubsub_handler(event, context):
    if "data" not in event:
        logging.warning("No data found in event.")
        return

    payload = base64.b64decode(event["data"]).decode("utf-8")
    message = json.loads(payload)

    blob_name = message.get("name", "unknown.jpg")
    logging.info(f"New file: {blob_name}")

    send_push_notification(blob_name)
