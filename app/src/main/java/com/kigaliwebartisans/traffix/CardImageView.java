package com.kigaliwebartisans.traffix;

import android.content.Context;
import android.graphics.Bitmap;
import android.widget.ImageView;

public class CardImageView extends androidx.appcompat.widget.AppCompatImageView {

    public CardImageView(Context context, Bitmap bitmap) {
        super(context);
        setImageBitmap(bitmap);
        setAdjustViewBounds(true);
        setPadding(16, 16, 16, 16);
    }
}
