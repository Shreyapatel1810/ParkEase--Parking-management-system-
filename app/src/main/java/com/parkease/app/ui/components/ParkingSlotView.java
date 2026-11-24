package com.parkease.app.ui.components;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.parkease.app.R;
import com.parkease.app.databinding.ViewParkingSlotBinding;

/**
 * One parking slot box for the slot selection grid.
 * Colour and icon change with the status:
 * <ul>
 *   <li>AVAILABLE   – green, can be selected</li>
 *   <li>RESERVED    – amber, clock icon</li>
 *   <li>OCCUPIED    – red, car icon</li>
 *   <li>MAINTENANCE – grey, wrench icon</li>
 *   <li>selected    – teal with a tick (only possible when AVAILABLE)</li>
 * </ul>
 * XML: {@code <com.parkease.app.ui.components.ParkingSlotView app:slotLabel="A-01" app:slotStatus="available" />}
 */
public class ParkingSlotView extends LinearLayout {

    public enum Status { AVAILABLE, RESERVED, OCCUPIED, MAINTENANCE }

    private final ViewParkingSlotBinding binding;
    private Status status = Status.AVAILABLE;
    private boolean slotSelected = false;
    private String label = "";

    public ParkingSlotView(@NonNull Context context) {
        this(context, null);
    }

    public ParkingSlotView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setClickable(true);
        setFocusable(true);
        int pad = getResources().getDimensionPixelSize(R.dimen.space_sm);
        setPadding(pad, pad, pad, pad);
        setMinimumWidth(getResources().getDimensionPixelSize(R.dimen.slot_width));
        setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.slot_height));

        binding = ViewParkingSlotBinding.inflate(LayoutInflater.from(context), this);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ParkingSlotView);
        try {
            String text = a.getString(R.styleable.ParkingSlotView_slotLabel);
            label = text == null ? "" : text;
            status = Status.values()[a.getInt(R.styleable.ParkingSlotView_slotStatus, 0)];
            slotSelected = a.getBoolean(R.styleable.ParkingSlotView_slotSelected, false);
        } finally {
            a.recycle();
        }
        render();
    }

    // ------------------------------------------------------------------ public API

    public void setLabel(@NonNull String label) {
        this.label = label;
        render();
    }

    @NonNull
    public String getLabel() {
        return label;
    }

    public void setStatus(@NonNull Status status) {
        this.status = status;
        if (status != Status.AVAILABLE) {
            slotSelected = false; // only free slots can stay selected
        }
        render();
    }

    @NonNull
    public Status getStatus() {
        return status;
    }

    /** True only for AVAILABLE slots – the customer may book these. */
    public boolean isBookable() {
        return status == Status.AVAILABLE;
    }

    /** Marks the slot as chosen by the customer. Ignored if the slot is not available. */
    public void setSlotSelected(boolean selected) {
        this.slotSelected = selected && isBookable();
        render();
    }

    public boolean isSlotSelected() {
        return slotSelected;
    }

    /** Human-readable status, e.g. "Occupied" – used in messages. */
    @NonNull
    public String getStatusName() {
        return getContext().getString(statusNameRes());
    }

    // ------------------------------------------------------------------ drawing

    private void render() {
        @DrawableRes int background;
        @DrawableRes int icon;
        @ColorRes int accent;
        @ColorRes int labelColor = R.color.text_primary;
        @StringRes int shortText;

        if (slotSelected) {
            background = R.drawable.bg_slot_selected_ripple;
            icon = R.drawable.ic_check_circle;
            accent = R.color.brand_on_secondary;
            labelColor = R.color.brand_on_secondary;
            shortText = R.string.slot_short_selected;
        } else {
            switch (status) {
                case RESERVED:
                    background = R.drawable.bg_slot_reserved_ripple;
                    icon = R.drawable.ic_clock;
                    accent = R.color.slot_reserved;
                    shortText = R.string.slot_short_reserved;
                    break;
                case OCCUPIED:
                    background = R.drawable.bg_slot_occupied_ripple;
                    icon = R.drawable.ic_car;
                    accent = R.color.slot_occupied;
                    shortText = R.string.slot_short_occupied;
                    break;
                case MAINTENANCE:
                    background = R.drawable.bg_slot_maintenance_ripple;
                    icon = R.drawable.ic_build;
                    accent = R.color.slot_maintenance;
                    labelColor = R.color.text_secondary;
                    shortText = R.string.slot_short_maintenance;
                    break;
                case AVAILABLE:
                default:
                    background = R.drawable.bg_slot_available_ripple;
                    icon = 0; // a free slot shows no icon
                    accent = R.color.slot_available;
                    shortText = R.string.slot_short_available;
                    break;
            }
        }

        int accentColor = ContextCompat.getColor(getContext(), accent);
        setBackgroundResource(background);

        if (icon == 0) {
            binding.imgSlotIcon.setVisibility(View.GONE);
        } else {
            binding.imgSlotIcon.setVisibility(View.VISIBLE);
            binding.imgSlotIcon.setImageResource(icon);
            ImageViewCompat.setImageTintList(binding.imgSlotIcon, ColorStateList.valueOf(accentColor));
        }

        binding.tvSlotLabel.setText(label);
        binding.tvSlotLabel.setTextColor(ContextCompat.getColor(getContext(), labelColor));
        binding.tvSlotStatus.setText(shortText);
        binding.tvSlotStatus.setTextColor(accentColor);

        String state = slotSelected ? getContext().getString(R.string.status_selected) : getStatusName();
        setContentDescription(getContext().getString(R.string.cd_slot, label, state));
    }

    @StringRes
    private int statusNameRes() {
        switch (status) {
            case RESERVED:
                return R.string.status_reserved;
            case OCCUPIED:
                return R.string.status_occupied;
            case MAINTENANCE:
                return R.string.status_maintenance;
            case AVAILABLE:
            default:
                return R.string.status_available;
        }
    }
}
