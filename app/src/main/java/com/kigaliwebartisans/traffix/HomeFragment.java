package com.kigaliwebartisans.traffix;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.os.Bundle;
import androidx.fragment.app.Fragment;

import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

public class HomeFragment extends Fragment {
    public HomeFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.fragment_home, container, false);

    ImageView flagImage = view.findViewById(R.id.flag_image);

    TextView policeTitle = view.findViewById(R.id.police_title);
    policeTitle.setText("Police Révolutionnaire Congolaise");
    TextView title = view.findViewById(R.id.home_title);
    title.setText("Système de Permis de Conduire");
    TextView desc = view.findViewById(R.id.home_desc);
    desc.setText("Bienvenue dans l'application du Système de Permis de Conduire.");

    //Set images
    flagImage.setScaleX(0f);
    flagImage.setScaleY(0f);
    flagImage.setAlpha(0f);

    ObjectAnimator scaleX = ObjectAnimator.ofFloat(flagImage, "scaleX", 0f, 1f);
    ObjectAnimator scaleY = ObjectAnimator.ofFloat(flagImage, "scaleY", 0f, 1f);
    ObjectAnimator fadeIn = ObjectAnimator.ofFloat(flagImage, "alpha", 0f, 1f);

    scaleX.setInterpolator(new android.view.animation.BounceInterpolator());
    scaleY.setInterpolator(new android.view.animation.BounceInterpolator());

    AnimatorSet animatorSet = new AnimatorSet();
    animatorSet.playTogether(scaleX, scaleY, fadeIn);
    animatorSet.setDuration(1200); // 1.2 seconds
    animatorSet.start();

    policeTitle.setAlpha(0f);
    title.setAlpha(0f);
    desc.setAlpha(0f);

    new Handler().postDelayed(() -> {
        policeTitle.animate().alpha(1f).setDuration(600).start();
        title.animate().alpha(1f).setDuration(600).start();
        desc.animate().alpha(1f).setDuration(600).start();
    }, 1200);



        return view;
    }
}
