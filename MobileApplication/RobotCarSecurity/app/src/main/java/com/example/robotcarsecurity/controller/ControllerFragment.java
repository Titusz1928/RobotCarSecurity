package com.example.robotcarsecurity.controller;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Spinner;
import com.google.android.material.tabs.TabLayout;

import androidx.fragment.app.Fragment;

import com.example.robotcarsecurity.R;

import java.io.IOException;
import java.io.InputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.List;
import org.json.JSONArray;
import java.util.ArrayList;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Toast;

public class ControllerFragment extends Fragment {

    private ImageView imageView;

    private TextView distanceText;

    private Button btnForward;
    private Button btnBackward;
    private Button btnRight;
    private Button btnLeft;
    private Button btnSend;

    private EditText commandEditText;

    private Spinner spinnerDevices;

    private OkHttpClient client = new OkHttpClient();
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable imageUpdater;
    private Runnable distanceUpdater;

    private String mainUrl;
    private String imageUrl;
    private String dataUrl;

    private String API_KEY;



    public ControllerFragment() {
    }

    public static ControllerFragment newInstance() {
        return new ControllerFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_controller, container, false);
        imageView = view.findViewById(R.id.imageViewEsp32Feed);
        distanceText = view.findViewById(R.id.imageOverlayDistanceText);

        mainUrl = getString(R.string.prodUrl);
        //mainUrl = getString(R.string.realUrl);
        imageUrl = mainUrl + "/current_frame";
        dataUrl = mainUrl + "/current_data";

        API_KEY = getString(R.string.apikey);

        btnForward = view.findViewById(R.id.btnForward);
        btnBackward = view.findViewById(R.id.btnBackward);
        btnLeft = view.findViewById(R.id.btnLeft);
        btnRight = view.findViewById(R.id.btnRight);
        btnSend = view.findViewById(R.id.btnSend);

        commandEditText = view.findViewById(R.id.editTextSendCommand);

        TabLayout tabLayout = view.findViewById(R.id.tabLayout);
        tabLayout.addTab(tabLayout.newTab().setText("Controls"));
        tabLayout.addTab(tabLayout.newTab().setText("Commands"));
        tabLayout.addTab(tabLayout.newTab().setText("Devices"));

        spinnerDevices = view.findViewById(R.id.deviceSpinner);
        fetchAndPopulateDevices();

        btnForward.setOnClickListener(v -> sendCommand("FORWARD"));
        btnBackward.setOnClickListener(v -> sendCommand("BACKWARD"));
        btnLeft.setOnClickListener(v -> sendCommand("LEFT"));
        btnRight.setOnClickListener(v -> sendCommand("RIGHT"));
        btnSend.setOnClickListener(v -> {
            String command = commandEditText.getText().toString().trim();
            if (!command.isEmpty()) {
                sendCommand(command);
                commandEditText.setText("");
            } else {
                Toast.makeText(getContext(), "Please enter a command", Toast.LENGTH_SHORT).show();
            }
        });

        View layoutControls = view.findViewById(R.id.layoutControls);
        View layoutDevices = view.findViewById(R.id.layoutDevices);
        View layoutCommands = view.findViewById(R.id.layoutCommands);

        imageUpdater = new Runnable() {
            @Override
            public void run() {
                fetchAndDisplayImage();
                handler.postDelayed(this, 1000); // update every 1 second
            }
        };
        handler.post(imageUpdater);

        distanceUpdater = new Runnable() {
            @Override
            public void run() {
                fetchAndDisplayData();
                handler.postDelayed(this, 1000); // update every 1 second
            }
        };
        handler.post(distanceUpdater);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switch (tab.getPosition()) {
                    case 0:  // Controls
                        layoutControls.setVisibility(View.VISIBLE);
                        layoutCommands.setVisibility(View.GONE);
                        layoutDevices.setVisibility(View.GONE);
                        break;
                    case 1:  // Commands
                        layoutControls.setVisibility(View.GONE);
                        layoutCommands.setVisibility(View.VISIBLE);
                        layoutDevices.setVisibility(View.GONE);
                        break;
                    case 2:  // Devices
                        layoutControls.setVisibility(View.GONE);
                        layoutCommands.setVisibility(View.GONE);
                        layoutDevices.setVisibility(View.VISIBLE);
                        break;
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        return view;
    }

    private void fetchAndDisplayImage() {
        String deviceId = getSelectedDeviceId();
        if (deviceId == null) return;

        String urlWithDevice = imageUrl + "?device_id=" + deviceId;

        Request request = new Request.Builder()
                .url(urlWithDevice)
                .addHeader("X-API-KEY", API_KEY)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    InputStream inputStream = response.body().byteStream();
                    final Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                    handler.post(() -> imageView.setImageBitmap(bitmap));
                }
            }
        });
    }

    private void fetchAndDisplayData() {
        String deviceId = getSelectedDeviceId();
        if (deviceId == null) return;

        String urlWithDevice = dataUrl + "?device_id=" + deviceId;

        Request request = new Request.Builder()
                .url(urlWithDevice)
                .addHeader("X-API-KEY", API_KEY)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String jsonString = response.body().string();
                    try {
                        JSONObject jsonObject = new JSONObject(jsonString);
                        final String distance = jsonObject.getString("data");
                        handler.post(() -> distanceText.setText("Distance: " + distance + " cm"));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void sendCommand(String command) {
        String deviceId = getSelectedDeviceId();
        if (deviceId == null) {
            System.err.println("No device selected.");
            return;
        }

        Request request = new Request.Builder()
                .url(mainUrl+"/send")
                .addHeader("X-API-KEY", API_KEY)
                .post(new okhttp3.FormBody.Builder()
                        .add("message", command)
                        .add("device_id", deviceId)
                        .build())
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    System.err.println("Command failed: " + response.code());
                }
            }
        });
    }


    private void fetchAndPopulateDevices() {
        Request request = new Request.Builder()
                .url(mainUrl+"/devices")
                .addHeader("X-API-KEY", API_KEY)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String jsonString = response.body().string();
                    try {
                        JSONArray jsonArray = new JSONArray(jsonString);
                        List<String> deviceList = new ArrayList<>();

                        for (int i = 0; i < jsonArray.length(); i++) {
                            deviceList.add(jsonArray.getString(i));
                        }

                        handler.post(() -> {
                            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                                    requireContext(),
                                    android.R.layout.simple_spinner_item,
                                    deviceList
                            );
                            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                            spinnerDevices.setAdapter(adapter);

                            //  refresh data when device selection changes
                            spinnerDevices.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                                @Override
                                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                                    fetchAndDisplayImage();
                                    fetchAndDisplayData();
                                }

                                @Override
                                public void onNothingSelected(AdapterView<?> parent) {}
                            });
                        });

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }


    private String getSelectedDeviceId() {
        Object selectedItem = spinnerDevices.getSelectedItem();
        return selectedItem != null ? selectedItem.toString() : null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(imageUpdater);  // Stop updates when fragment destroyed
    }
}
