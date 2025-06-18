import torch
import numpy as np
import cv2
from typing import List, Tuple

#LOADING MODEL
model = torch.hub.load('ultralytics/yolov5', 'yolov5s', pretrained=True) 
model.conf = 0.4  # CONFIDENCE

def detect_people(image_bytes: bytes):
    np_arr = np.frombuffer(image_bytes, np.uint8)
    img = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)
    h, w = img.shape[:2]

    results = model(img)
    boxes = []
    for *xyxy, conf, cls in results.xyxy[0]:
        if model.names[int(cls)] == 'person':
            #print("person detector detected a person")
            x1, y1, x2, y2 = map(int, xyxy)
            boxes.append((x1, y1, x2, y2))

    return boxes, w, h
