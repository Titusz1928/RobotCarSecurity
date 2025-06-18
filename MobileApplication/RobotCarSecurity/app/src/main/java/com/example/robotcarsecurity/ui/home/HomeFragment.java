package com.example.robotcarsecurity.ui.home;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.robotcarsecurity.R;
import com.example.robotcarsecurity.databinding.FragmentHomeBinding;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;

    private TextView infoCardContent;

    private ImageView robotImageView;
    private View llInfoCardView;

    private LinearLayout buttonsLayout;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        HomeViewModel homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        llInfoCardView = root.findViewById(R.id.llInfoCardView);

        infoCardContent = root.findViewById(R.id.icvDescriptionTextView);  // Content to hide/show

        robotImageView = root.findViewById(R.id.imageView2);

        buttonsLayout = root.findViewById(R.id.expansion_panel_buttons);

        buttonsLayout.setVisibility(View.GONE);
        robotImageView.setVisibility(View.GONE);
        infoCardContent.setVisibility(View.GONE);

        llInfoCardView.setOnClickListener(v ->
                toggleCardVisibility(infoCardContent, robotImageView, buttonsLayout));

        root.setOnClickListener(v -> collapseCard());

        ImageButton browserButton = root.findViewById(R.id.llBrowserImageButton);

        browserButton.setOnClickListener(v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.prodUrl)));
            startActivity(browserIntent);
        });

        llInfoCardView.setClickable(true);

        return root;
    }

    private void toggleCardVisibility(View... views) {
        if (views[0].getVisibility() == View.VISIBLE) {
            collapseCard(views);
        } else {
            expandCard(views);
        }
    }


    private void expandCard(View... views) {
        for (View view : views) {
            view.setVisibility(View.VISIBLE);
        }
    }
    
    private void collapseCard(View... views) {
        for (View view : views) {
            view.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}