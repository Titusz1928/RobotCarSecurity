package com.example.robotcarsecurity.detections;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.robotcarsecurity.R;

import com.example.robotcarsecurity.Models.DetectionImage;

import java.util.List;

public class DetectionsAdapter extends RecyclerView.Adapter<DetectionsAdapter.ViewHolder> {

    private List<DetectionImage> imageItems;
    private Context context;

    public DetectionsAdapter(Context context, List<DetectionImage> imageItems) {
        this.context = context;
        this.imageItems = imageItems;
    }

    @NonNull
    @Override
    public DetectionsAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_detection_image, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DetectionsAdapter.ViewHolder holder, int position) {
        DetectionImage item = imageItems.get(position);
        Glide.with(context)
                .load(item.getUrl())
                .placeholder(R.drawable.logo)
                .into(holder.imageView);

        holder.textViewName.setText(item.getName());
        holder.textViewDate.setText(item.getCreated());
    }

    @Override
    public int getItemCount() {
        return imageItems.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView textViewName;
        TextView textViewDate;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageViewDetection);
            textViewName = itemView.findViewById(R.id.textViewImageName);
            textViewDate = itemView.findViewById(R.id.textViewImageDate);
        }
    }
}

