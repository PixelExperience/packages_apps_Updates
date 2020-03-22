package org.pixelexperience.ota;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
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

    @Override
    public void setClickable(boolean clickable) {
        super.setClickable(clickable);
        int[] attrs = new int[]{R.attr.selectableItemBackground};
        TypedArray typedArray = getContext().obtainStyledAttributes(attrs);
        int backgroundResource = typedArray.getResourceId(0, 0);
        setForeground(getResources().getDrawable(backgroundResource, getContext().getTheme()));
        typedArray.recycle();
    }

    public void setSummary(String summary) {
        summaryView.setText(summary);
    }

    public void setTitle(String title) {
        titleView.setText(title);
    }

    public void setImage(Drawable image) {
        imageView.setImageDrawable(image);
    }
}
