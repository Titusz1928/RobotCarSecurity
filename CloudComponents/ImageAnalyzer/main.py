import base64
import json
import io
import logging
from datetime import datetime

from google.cloud import storage, vision
from PIL import Image
import imagehash


# CONFIG

DEST_BUCKET = "es32car-images-processed"
PERSON_SCORE_THRESHOLD = 0.2
LOGGER = logging.getLogger(__name__)
LOGGER.setLevel(logging.INFO)


# SIMILARITY (pHash Hamming distance)
PHASH_IDENTICAL = 0
PHASH_HIGH_THRESHOLD = 25
PHASH_MEDIUM_THRESHOLD = 50

# TIME THRESHOLDS (seconds)
TIME_SHORT = 300
TIME_MEDIUM = 1200

storage_client = storage.Client() 
vision_client = vision.ImageAnnotatorClient()


def download_blob(bucket_name: str, blob_name: str) -> bytes:
    bucket = storage_client.bucket(bucket_name)
    blob = bucket.blob(blob_name)
    return blob.download_as_bytes()



def should_upload(new_img_bytes: bytes) -> bool:
    dest_bucket = storage_client.bucket(DEST_BUCKET)
    blobs = list(dest_bucket.list_blobs(prefix=""))

    if not blobs:
        LOGGER.info("No previous images – upload allowed")
        return True

    latest_blob = max(blobs, key=lambda b: b.updated or b.time_created)
    prev_bytes = latest_blob.download_as_bytes()

    # Calculate similarity
    new_hash = imagehash.phash(Image.open(io.BytesIO(new_img_bytes)))
    prev_hash = imagehash.phash(Image.open(io.BytesIO(prev_bytes)))
    distance = new_hash - prev_hash
    LOGGER.info(f"pHash distance = {distance}")

    # Categorize similarity
    if distance == 0:
        similarity = "identical"
    elif distance <= PHASH_HIGH_THRESHOLD:
        similarity = "high"
    elif distance <= PHASH_MEDIUM_THRESHOLD:
        similarity = "medium"
    else:
        similarity = "low"

    # Calculate time since last upload
    time_diff_sec = (datetime.now(tz=latest_blob.updated.tzinfo) - latest_blob.updated).total_seconds()
    LOGGER.info(f"Time since last upload: {time_diff_sec:.1f} seconds")

    # Categorize time
    if time_diff_sec < TIME_SHORT:
        time_cat = "short"
    elif time_diff_sec < TIME_MEDIUM:
        time_cat = "medium"
    else:
        time_cat = "long"

    # Decision matrix
    decision_table = {
        ("identical", "short"): False,
        ("identical", "medium"): False,
        ("identical", "long"): False,

        ("high", "short"): False,
        ("high", "medium"): False,
        ("high", "long"): True,

        ("medium", "short"): False,
        ("medium", "medium"): True,
        ("medium", "long"): True,

        ("low", "short"): True,
        ("low", "medium"): True,
        ("low", "long"): True,
    }

    decision = decision_table[(similarity, time_cat)]
    LOGGER.info(f"Decision: {'Upload' if decision else 'Skip'} ({similarity} similarity, {time_cat} time)")
    return decision


def contains_person(img_bytes: bytes) -> bool:
    """
    Uses Cloud Vision Object Localization: returns True if a 'Person'
    object is detected with sufficient confidence.
    """
    image = vision.Image(content=img_bytes)
    response = vision_client.object_localization(image=image)
    for obj in response.localized_object_annotations:
        if obj.name.lower() == "person" and obj.score >= PERSON_SCORE_THRESHOLD:
            LOGGER.info(f"Person found with confidence {obj.score:.2f}")
            return True
    LOGGER.info("No person detected.")
    return False



def process_image(event, context):
    try:
        payload = base64.b64decode(event["data"]).decode("utf-8")
        msg = json.loads(payload)
        bucket_name = msg["bucket"]
        blob_name   = msg["name"]

        LOGGER.info(f"Received {bucket_name}/{blob_name}")

        img_bytes = download_blob(bucket_name, blob_name)

        if not should_upload(img_bytes):
            LOGGER.info("Image skipped based on similarity/time logic.")
            return

        if not contains_person(img_bytes):
            LOGGER.info("Not a person – skipping copy.")
            return

        # copy to processed bucket
        src_bucket  = storage_client.bucket(bucket_name)
        src_blob    = src_bucket.blob(blob_name)
        dest_bucket = storage_client.bucket(DEST_BUCKET)

        src_bucket.copy_blob(src_blob, dest_bucket, blob_name)
        LOGGER.info(f"Copied to {DEST_BUCKET}/{blob_name}")

    except Exception as exc:
        LOGGER.exception(f"Processing failed: {exc}")
        raise

    finally:
        try:
            storage_client.bucket(bucket_name).blob(blob_name).delete()
            LOGGER.info(f"Deleted original {bucket_name}/{blob_name}")
        except Exception as del_err:
            LOGGER.error(f"Could not delete {bucket_name}/{blob_name}: {del_err}")
