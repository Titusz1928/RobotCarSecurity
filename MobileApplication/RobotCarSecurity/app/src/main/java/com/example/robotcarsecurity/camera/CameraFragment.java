package com.example.robotcarsecurity.camera;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
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

public class CameraFragment extends Fragment {

    private PreviewView previewView;
    private WebSocketClient webSocketClient;
    private String deviceId;
    private String mainUrl;
    private ExecutorService cameraExecutor;

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable imageSendRunnable;
    private Bitmap latestFrame;
    private volatile boolean isSending = false;

    private static final int CAMERA_REQUEST_CODE = 100;

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
            startCamera();
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
            // time-based ID
            id = "mobildevice_" + System.currentTimeMillis();
            prefs.edit().putString("device_id", id).apply();
        }

        return id;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(requireContext());

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, image -> {
                    //Log.d("MyLog", "Frame received"); // <-- Add this
                    latestFrame = imageToBitmap(image);
                    image.close();
                });

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();

                cameraProvider.bindToLifecycle(
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
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(), "Device connected successfully!", Toast.LENGTH_SHORT).show()
                    );
                    webSocketClient.send("device_id:" + deviceId);
                    startSendingImages();
                }

                @Override
                public void onMessage(String message) {
                    Log.d("WebSocket", "Text received: " + message);
                }

                @Override
                public void onMessage(ByteBuffer bytes) {
                    Log.d("WebSocket", "Binary received: " + bytes.remaining() + " bytes");
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.d("WebSocket", "Closed: " + reason);
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(), "Disconnected: " + reason, Toast.LENGTH_SHORT).show()
                    );
                    stopSendingImages();
                }

                @Override
                public void onError(Exception ex) {
                    Log.e("WebSocket", "Error", ex);
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(), "Connection error!", Toast.LENGTH_SHORT).show()
                    );
                    stopSendingImages();
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

    private Bitmap imageToBitmap(ImageProxy image) {
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
    }

    @Override
    public void onDestroy() {
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
