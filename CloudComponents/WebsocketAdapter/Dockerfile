# Use official Python runtime as a parent image
FROM python:3.10-slim

# Set working directory
WORKDIR /app

# Copy requirements and install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy app source code
COPY . .

# Expose port 8080 (Cloud Run default)
EXPOSE 8080

# Run your python file (replace with your filename, e.g. adapter.py)
CMD ["python", "adapter.py"]
