import cv2
import numpy as np

def detect_lane_direction(image_bytes: bytes, threshold=50):
    np_arr = np.frombuffer(image_bytes, np.uint8)
    img = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)

    height, width = img.shape[:2]
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    blur = cv2.GaussianBlur(gray, (5, 5), 0)
    edges = cv2.Canny(blur, 50, 150)

    # ROI mask
    mask = np.zeros_like(edges)
    polygon = np.array([[
        (0, height),
        (width, height),
        (width, int(height * 0.6)),
        (0, int(height * 0.6)),
    ]], np.int32)
    cv2.fillPoly(mask, polygon, 255)
    cropped_edges = cv2.bitwise_and(edges, mask)

    lines = cv2.HoughLinesP(cropped_edges, 1, np.pi / 180, 50, maxLineGap=50)
    lane_lines = []

    if lines is not None:
        for line in lines:
            x1, _, x2, _ = line[0]
            lane_lines.append((x1, x2))

    if len(lane_lines) >= 2:
        left_lane = min(lane_lines, key=lambda x: x[0])
        right_lane = max(lane_lines, key=lambda x: x[1])
        lane_center = (left_lane[0] + right_lane[1]) // 2
    else:
        lane_center = width // 2

    car_center = width // 2
    offset = lane_center - car_center

    if abs(offset) < threshold:
        return "center"
    elif offset > 0:
        return "right"
    else:
        return "left"
