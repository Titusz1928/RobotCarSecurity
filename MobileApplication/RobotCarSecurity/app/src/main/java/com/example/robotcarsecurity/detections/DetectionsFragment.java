package com.example.robotcarsecurity.detections;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.robotcarsecurity.Models.DetectionImage;
import com.example.robotcarsecurity.R;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.ArrayList;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DetectionsFragment extends Fragment {

    private RecyclerView recyclerView;
    private DetectionsAdapter adapter;
    private OkHttpClient client = new OkHttpClient();
    private static final String TAG = "DetectionsFragment";

    private String API_KEY;

    private String mainUrl;
    private String detectionsURL;

    public DetectionsFragment() {}

    public static DetectionsFragment newInstance() {
        return new DetectionsFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_detections, container, false);
        recyclerView = view.findViewById(R.id.recyclerViewDetections);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        mainUrl = getString(R.string.prodUrl);
        //mainUrl = getString(R.string.realUrl);
        detectionsURL=mainUrl+"/api/detections?count=10";

        API_KEY = getString(R.string.apikey);

        fetchImages();
        return view;
    }

    private void fetchImages() {
        Request request = new Request.Builder()
                .url(detectionsURL)
                .addHeader("X-API-KEY", API_KEY)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to fetch images", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Unexpected response: " + response);
                    return;
                }

                String json = response.body().string();
                Gson gson = new Gson();
                Type listType = new TypeToken<List<DetectionImage>>() {}.getType();
                List<DetectionImage> images = gson.fromJson(json, listType);

                new Handler(Looper.getMainLooper()).post(() -> {
                    adapter = new DetectionsAdapter(getContext(), images);  // pass full items now
                    recyclerView.setAdapter(adapter);
                });
            }
        });
    }

    private List<String> extractUrls(List<DetectionImage> images) {
        List<String> urls = new ArrayList<>();
        for (DetectionImage item : images) {
            urls.add(item.getUrl());
        }
        return urls;
    }

}
