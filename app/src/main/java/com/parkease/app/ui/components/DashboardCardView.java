package com.parkease.app.ui.components;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.google.android.material.card.MaterialCardView;
import com.parkease.app.R;
import com.parkease.app.databinding.ViewDashboardCardBinding;

/**
 * Statistic card for dashboards: coloured icon, big value, title and optional subtitle.
 *
 * XML:
 * <pre>
 * &lt;com.parkease.app.ui.components.DashboardCardView
 *     android:layout_width="0dp"
 *     android:layout_height="wrap_content"
 *     android:layout_weight="1"
 *     app:dcIcon="@drawable/ic_rupee"
 *     app:dcTitle="@string/..."
 *     app:dcValue="₹4,520"
 *     app:dcSubtitle="+12% vs yesterday"
 *     app:dcAccent="teal" /&gt;
 * </pre>
 * Java: {@code card.setValue("₹5,100");}
 */
public class DashboardCardView extends MaterialCardView {

    /** Colour theme of the icon and subtitle. */
    public enum Accent { TEAL, NAVY, GREEN, AMBER, RED }

    private final ViewDashboardCardBinding binding;

    public DashboardCardView(@NonNull Context context) {
        this(context, null);
    }

    public DashboardCardView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, com.google.android.material.R.attr.materialCardViewStyle);
    }

    public DashboardCardView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        binding = ViewDashboardCardBinding.inflate(LayoutInflater.from(context), this);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.DashboardCardView);
        try {
            int icon = a.getResourceId(R.styleable.DashboardCardView_dcIcon, R.drawable.ic_dashboard);
            setIcon(icon);
            setTitle(a.getString(R.styleable.DashboardCardView_dcTitle));
            setValue(a.getString(R.styleable.DashboardCardView_dcValue));
            setSubtitle(a.getString(R.styleable.DashboardCardView_dcSubtitle));
            int accent = a.getInt(R.styleable.DashboardCardView_dcAccent, 0);
            setAccent(Accent.values()[accent]);
        } finally {
            a.recycle();
        }
    }

    public void setIcon(@DrawableRes int iconRes) {
        binding.imgIcon.setImageResource(iconRes);
    }

    public void setTitle(@Nullable CharSequence title) {
        binding.tvTitle.setText(title);
        updateDescription();
    }

    public void setValue(@Nullable CharSequence value) {
        binding.tvValue.setText(value);
        updateDescription();
    }

    /** Small line under the title, e.g. "+12% vs yesterday". Hidden when empty. */
    public void setSubtitle(@Nullable CharSequence subtitle) {
        binding.tvSubtitle.setText(subtitle);
        binding.tvSubtitle.setVisibility(TextUtils.isEmpty(subtitle) ? View.GONE : View.VISIBLE);
    }

    public void setAccent(@NonNull Accent accent) {
        @ColorRes int bg;
        @ColorRes int fg;
        switch (accent) {
            case NAVY:
                bg = R.color.accent_navy_bg;
                fg = R.color.accent_navy;
                break;
            case GREEN:
                bg = R.color.slot_available_bg;
                fg = R.color.slot_available;
                break;
            case AMBER:
                bg = R.color.slot_reserved_bg;
                fg = R.color.slot_reserved;
                break;
            case RED:
                bg = R.color.slot_occupied_bg;
                fg = R.color.slot_occupied;
                break;
            case TEAL:
            default:
                bg = R.color.brand_secondary_container;
                fg = R.color.brand_secondary;
                break;
        }
        int fgColor = ContextCompat.getColor(getContext(), fg);
        binding.iconContainer.setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(getContext(), bg)));
        ImageViewCompat.setImageTintList(binding.imgIcon, ColorStateList.valueOf(fgColor));
        binding.tvSubtitle.setTextColor(fgColor);
    }

    /** Screen readers announce "Today's revenue, ₹4,520". */
    private void updateDescription() {
        setContentDescription(binding.tvTitle.getText() + ", " + binding.tvValue.getText());
    }
}
