package org.pixelexperience.ota;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;

public class ExtraCardView extends CardView {
    TextView titleView;
    TextView summaryView;
    ImageView imageView;

    public ExtraCardView(@NonNull Context context) {
        this(context, null, 0);
    }

    public ExtraCardView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ExtraCardView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        addView(inflate(getContext(), R.layout.extra_card_view, null));
        titleView = findViewById(R.id.card_title);
        summaryView = findViewById(R.id.card_summary);
        imageView = findViewById(R.id.card_image);

        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.extra_card_view,
                0, 0);

        try {
            titleView.setText(a.getString(R.styleable.extra_card_view_title));
            summaryView.setText(a.getString(R.styleable.extra_card_view_summary));
            imageView.setImageDrawable(a.getDrawable(R.styleable.extra_card_view_image));
        } finally {
            a.recycle();
        }
    }

    public void setSummary(String summary) {
        summaryView.setText(summary);
    }
}
