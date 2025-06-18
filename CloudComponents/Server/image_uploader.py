from google.cloud import storage
import uuid
from datetime import datetime
import io
import os
import logging

logging.basicConfig(
    level=logging.DEBUG,
    format='[%(asctime)s] %(levelname)s in %(module)s: %(message)s',
)

from dotenv import load_dotenv
load_dotenv()


BUCKET_NAME = os.getenv("PREPROCESSED_BUCKET")

def upload_image_to_gcs(image_bytes):
    logging.debug(f"Uploading to bucket: {BUCKET_NAME}")

    timestamp = datetime.utcnow().strftime("%Y%m%d_%H%M%S")
    filename = f"person_{timestamp}_{uuid.uuid4().hex[:8]}.jpg"

    # INITIALIZING CLIENT
    storage_client = storage.Client()
    bucket = storage_client.bucket(BUCKET_NAME)
    blob = bucket.blob(filename)

    blob.upload_from_file(io.BytesIO(image_bytes), content_type="image/jpeg")

    logging.debug(f"Uploaded image to GCS: {filename}")
    print(f"Uploaded image to GCS: {filename}")
