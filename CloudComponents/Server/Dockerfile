FROM python:3.10-slim

# Prevent interactive prompts during package installs
ENV DEBIAN_FRONTEND=noninteractive

# Set environment variables for YOLO
ENV YOLO_CONFIG_DIR=/app/.config
ENV TORCH_HOME=/app/.cache

# Install system dependencies
RUN apt-get update && apt-get install -y \
    libgl1 \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender1 \
    libopenblas-dev \
    libgomp1 \
    libjpeg-dev \
    zlib1g-dev \
    libfreetype6-dev \
    libpng-dev \
    && rm -rf /var/lib/apt/lists/*

# Create directories for YOLO cache
RUN mkdir -p /app/.config /app/.cache

# Set working directory
WORKDIR /app

# Copy requirements first for better caching
COPY requirements.txt .

# Install Python dependencies
RUN pip install --no-cache-dir -r requirements.txt

# Copy the rest of your application code
COPY . .

# Set environment variable for port
ENV PORT=8080

EXPOSE 8080

# Run with eventlet directly (best for SocketIO)
CMD ["python", "main.py"]