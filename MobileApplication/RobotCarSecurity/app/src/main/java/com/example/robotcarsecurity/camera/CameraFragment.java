package com.example.robotcarsecurity.camera;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.robotcarsecurity.R;
import com.google.common.util.concurrent.ListenableFuture;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.Manifest;
import android.content.pm.PackageManager;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

public class CameraFragment extends Fragment {

    private PreviewView previewView;
    private WebSocketClient webSocketClient;
    private String deviceId;
    private String mainUrl;
    private ExecutorService cameraExecutor;

    private androidx.camera.core.Camera camera;

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable imageSendRunnable;
    private Bitmap latestFrame;
    private volatile boolean isSending = false;
    private boolean isReconnecting = false;
    private static final int CAMERA_REQUEST_CODE = 100;

    private static final long RECONNECT_DELAY_MS = 5000;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_camera, container, false);
        previewView = view.findViewById(R.id.previewView);

        mainUrl = getString(R.string.prodUrlWSS);
        Log.d("MyLog", "Fragment opened");

        cameraExecutor = Executors.newSingleThreadExecutor();

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_REQUEST_CODE);
        } else {
            // Permission already granted — you can start using the camera
            startCamera(); // or your image capture code
        }


        deviceId = getOrCreateDeviceId();
        Log.d("MyLog", "Device ID: " + deviceId);
        connectWebSocket();

        return view;
    }
    private String getOrCreateDeviceId() {
        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        String id = prefs.getString("device_id", null);

        if (id == null) {
            // Use time-based ID, you could also append a random number or UUID if desired
            id = "mobildevice_" + System.currentTimeMillis();
            prefs.edit().putString("device_id", id).apply();
        }

        return id;
    }

    @OptIn(markerClass = ExperimentalGetImage.class) private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(requireContext());

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Create the Preview use case
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // Create the ImageAnalysis use case
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
                    Image image = imageProxy.getImage();
                    if (image != null) {
                        Log.d("MyLog", "Bitmap size: " + image.getWidth() + "x" + image.getHeight());
                        Log.d("MyLog", "Image format: " + image.getFormat());  // Expected: 35 for YUV_420_888

                        Image.Plane[] planes = image.getPlanes();
                        for (int i = 0; i < planes.length; i++) {
                            Log.d("MyLog", "Plane " + i + " pixelStride: " + planes[i].getPixelStride() +
                                    ", rowStride: " + planes[i].getRowStride());
                        }

                        // Your processing method
                        latestFrame = imageToBitmap(imageProxy);
                    }

                    imageProxy.close(); // Always close the proxy to avoid memory leaks
                });

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                // Unbind use cases before rebinding
                cameraProvider.unbindAll();

                // Bind to lifecycle with BOTH preview and imageAnalysis
                camera = cameraProvider.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        imageAnalysis
                );

            } catch (Exception e) {
                Log.e("MyLog", "Failed to start camera", e);
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    /*private Bitmap imageToBitmap(ImageProxy image) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }*/

    private void connectWebSocket() {
        try {
            URI uri = new URI(mainUrl);
            webSocketClient = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    Log.d("MyLog", "Connected");
                    isReconnecting = false;
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(), "Device connected successfully!", Toast.LENGTH_SHORT).show()
                    );
                    webSocketClient.send("device_id:" + deviceId);
                    startSendingImages();
                }

                @Override
                public void onMessage(String message) {
                    Log.d("WebSocket", "Text received: " + message);
                    if (message.equalsIgnoreCase("automaticping")) {
                        webSocketClient.send("automaticpong");
                        Log.d("WebSocket", "Sent automaticpong in response to automaticping");
                    }
                    if (message.equalsIgnoreCase("ping")) {
                        webSocketClient.send("pong");
                        Log.d("WebSocket", "Sent pong in response to ping");
                    }
                    if (message.equalsIgnoreCase("esp32_led_on")) {
                        if (camera != null) {
                            camera.getCameraControl().enableTorch(true);
                            Log.d("WebSocket", "Torch turned ON");
                        }
                    }
                    if (message.equalsIgnoreCase("esp32_led_off")) {
                        if (camera != null) {
                            camera.getCameraControl().enableTorch(false);
                            Log.d("WebSocket", "Torch turned OFF");
                        }
                    }
                }

                @Override
                public void onMessage(ByteBuffer bytes) {
                    Log.d("WebSocket", "Binary received: " + bytes.remaining() + " bytes");
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.d("WebSocket", "Closed: " + reason);

                    FragmentActivity activity = getActivity();
                    if (isAdded() && activity != null) {
                        activity.runOnUiThread(() ->
                                Toast.makeText(getContext(), "Disconnected: " + reason, Toast.LENGTH_SHORT).show()
                        );
                    }

                    stopSendingImages();
                    scheduleReconnect();
                }

                @Override
                public void onError(Exception ex) {
                    Log.e("WebSocket", "Error", ex);

                    FragmentActivity activity = getActivity();
                    if (isAdded() && activity != null) {
                        activity.runOnUiThread(() ->
                                Toast.makeText(getContext(), "Connection error!", Toast.LENGTH_SHORT).show()
                        );
                    }

                    stopSendingImages();
                    scheduleReconnect();
                }
            };
            webSocketClient.connect();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    private void startSendingImages() {
        imageSendRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isSending) {
                    boolean hasImage = latestFrame != null;
                    boolean wsOpen = webSocketClient != null && webSocketClient.isOpen();

                    if (hasImage && wsOpen) {
                        isSending = true;
                        sendImageToServer(latestFrame);
                        isSending = false;
                    }
                }
                handler.postDelayed(this, 200);
            }
        };
        handler.post(imageSendRunnable);
    }

    private void stopSendingImages() {
        handler.removeCallbacks(imageSendRunnable);
    }

    private void sendImageToServer(Bitmap bitmap) {
        // Resize the bitmap to 320x240
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 320, 240, true);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos); // You can also reduce quality here
        byte[] imageBytes = baos.toByteArray();

        if (webSocketClient != null && webSocketClient.isOpen()) {
            webSocketClient.send(imageBytes);
            Log.d("MyLog", "Sent image: " + imageBytes.length + " bytes");
        } else {
            Log.e("WebSocket", "WebSocket not open");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
                startCamera(); // or your camera image capture logic
            } else {
                Log.e("Camera", "Camera permission denied.");
            }
        }
    }

    private void scheduleReconnect() {
        if (isReconnecting) return;

        isReconnecting = true;
        handler.postDelayed(() -> {
            Log.d("WebSocket", "Attempting to reconnect...");
            connectWebSocket();  // Try to reconnect
            isReconnecting = false;
        }, RECONNECT_DELAY_MS);
    }


    private Bitmap imageToBitmap(ImageProxy image) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11+
            //Log.d("MyLog", "Using NEW YUV420 converter");
            return convertYUV420ToBitmap_New(image);
        } else {
            //Log.d("MyLog", "Using OLD YUV420 converter");
            return convertYUV420ToBitmap_Old(image);
        }
    }

    private Bitmap convertYUV420ToBitmap_Old(ImageProxy image) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];

        // NV21 layout: Y + V + U
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21,
                image.getWidth(), image.getHeight(), null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 90, out);
        byte[] jpegBytes = out.toByteArray();

        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
    }

    private Bitmap convertYUV420ToBitmap_New(ImageProxy image) {
        byte[] nv21 = YUV_420_888toNV21(image);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21,
                image.getWidth(), image.getHeight(), null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 90, out);
        byte[] jpegBytes = out.toByteArray();

        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
    }

    private static byte[] YUV_420_888toNV21(ImageProxy image) {
        int width = image.getWidth();
        int height = image.getHeight();

        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int yRowStride = planes[0].getRowStride();
        int yPixelStride = planes[0].getPixelStride();
        int uRowStride = planes[1].getRowStride();
        int uPixelStride = planes[1].getPixelStride();

        byte[] nv21 = new byte[width * height * 3 / 2];

        // Copy Y plane
        int pos = 0;
        for (int row = 0; row < height; row++) {
            int yBufferPos = row * yRowStride;
            for (int col = 0; col < width; col++) {
                nv21[pos++] = yBuffer.get(yBufferPos + col * yPixelStride);
            }
        }

        // Copy UV plane interleaved as VU for NV21
        int chromaHeight = height / 2;
        int chromaWidth = width / 2;

        for (int row = 0; row < chromaHeight; row++) {
            int uBufferPos = row * uRowStride;
            int vBufferPos = row * planes[2].getRowStride();

            for (int col = 0; col < chromaWidth; col++) {
                // Make sure indices do not go out of bounds
                int uIndex = uBufferPos + col * uPixelStride;
                int vIndex = vBufferPos + col * planes[2].getPixelStride();

                if (vIndex >= vBuffer.limit() || uIndex >= uBuffer.limit()) {
                    // Defensive: break if indexes out of bounds
                    break;
                }

                nv21[pos++] = vBuffer.get(vIndex); // V
                nv21[pos++] = uBuffer.get(uIndex); // U
            }
        }

        return nv21;
    }


   /* private Bitmap imageToBitmap(ImageProxy image) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];

        // U and V are swapped
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21,
                image.getWidth(), image.getHeight(), null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 90, out);
        byte[] jpegBytes = out.toByteArray();

        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
    }*/

    @Override
    public void onDestroy() {
        cameraExecutor.shutdown();
        super.onDestroy();
        stopSendingImages();
        if (webSocketClient != null) {
            webSocketClient.close();
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }
}
