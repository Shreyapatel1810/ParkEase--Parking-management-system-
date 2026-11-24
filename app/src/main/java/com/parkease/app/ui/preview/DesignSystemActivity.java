package com.parkease.app.ui.preview;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.GridLayout;

import androidx.annotation.ColorRes;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;
import com.parkease.app.MainActivity;
import com.parkease.app.R;
import com.parkease.app.databinding.ActivityDesignSystemBinding;
import com.parkease.app.databinding.ItemColorSwatchBinding;
import com.parkease.app.ui.common.BaseActivity;
import com.parkease.app.ui.components.ParkingSlotView;
import com.parkease.app.utils.ThemeManager;

/**
 * STEP 3 – Design System preview.
 * Shows every ParkEase colour, text style and component on one screen so the
 * design can be checked in light and dark mode before real screens are built.
 * This is the launcher screen for now; Splash/Login replace it in the next step.
 */
public class DesignSystemActivity extends BaseActivity {

    /** Colours shown in the swatch grid (names are in res/values/arrays.xml). */
    private static final int[] SWATCH_COLORS = {
            R.color.brand_primary, R.color.brand_secondary, R.color.bg_screen, R.color.bg_card,
            R.color.text_primary, R.color.text_secondary, R.color.stroke, R.color.error,
            R.color.slot_available, R.color.slot_reserved, R.color.slot_occupied, R.color.slot_maintenance
    };

    private static final long DEMO_LOADING_MS = 2000;

    private ActivityDesignSystemBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDesignSystemBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        applyStatusBarPadding(binding.header.getRoot());
        applyStatusBarScrim(binding.statusBarScrim.getRoot());

        setupHeader();
        setupThemeToggle();
        setupColorSwatches();
        setupInputs();
        setupSearchBar();
        setupParkingSlots();
        setupEmptyAndLoading();
        setupBottomNav();

        binding.btnOpenSetup.setOnClickListener(v ->
                startActivity(new Intent(this, MainActivity.class)));
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ------------------------------------------------------------------ header

    private void setupHeader() {
        binding.header.toolbar.setTitle(R.string.app_name);
        binding.header.tvHeaderTitle.setText(R.string.ds_title);
        binding.header.tvHeaderSubtitle.setText(R.string.ds_subtitle);
    }

    // ------------------------------------------------------------------ theme

    private void setupThemeToggle() {
        int mode = ThemeManager.getMode(this);
        int checkedId = mode == ThemeManager.MODE_LIGHT ? R.id.btnThemeLight
                : mode == ThemeManager.MODE_DARK ? R.id.btnThemeDark
                : R.id.btnThemeSystem;
        binding.toggleTheme.check(checkedId);   // set BEFORE the listener, so it doesn't fire

        binding.toggleTheme.addOnButtonCheckedListener((group, id, isChecked) -> {
            if (!isChecked) return;
            int newMode = id == R.id.btnThemeLight ? ThemeManager.MODE_LIGHT
                    : id == R.id.btnThemeDark ? ThemeManager.MODE_DARK
                    : ThemeManager.MODE_SYSTEM;
            if (newMode != ThemeManager.getMode(this)) {
                ThemeManager.setMode(this, newMode);   // screen is recreated in the new theme
            }
        });
    }

    // ------------------------------------------------------------------ colours

    private void setupColorSwatches() {
        String[] names = getResources().getStringArray(R.array.ds_swatch_names);
        LayoutInflater inflater = getLayoutInflater();
        for (int i = 0; i < SWATCH_COLORS.length; i++) {
            ItemColorSwatchBinding item = ItemColorSwatchBinding.inflate(inflater, binding.gridColors, false);
            item.swatch.setBackgroundTintList(ColorStateList.valueOf(color(SWATCH_COLORS[i])));
            item.tvSwatchName.setText(names[i]);

            // Each swatch takes an equal share of the row
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f));
            params.width = 0;
            binding.gridColors.addView(item.getRoot(), params);
        }
    }

    // ------------------------------------------------------------------ inputs

    private void setupInputs() {
        // Show what an error looks like
        binding.tilVehicle.setError(getString(R.string.ds_input_vehicle_error));
        binding.etVehicle.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) binding.tilVehicle.setError(null);
        });

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                getResources().getStringArray(R.array.vehicle_types));
        binding.dropdownVehicleType.setAdapter(adapter);
    }

    // ------------------------------------------------------------------ search

    private void setupSearchBar() {
        binding.searchBar.setOnSearchListener(query -> {
            if (!query.isEmpty()) {
                snack(getString(R.string.ds_search_result, query));
            }
        });
        binding.searchBar.setOnFilterClickListener(v -> snack(getString(R.string.ds_filter_clicked)));
    }

    // ------------------------------------------------------------------ slots

    /** Single selection: tapping a free slot selects it and clears the others. */
    private void setupParkingSlots() {
        GridLayout grid = binding.gridSlots;
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            if (!(child instanceof ParkingSlotView)) continue;
            ParkingSlotView slot = (ParkingSlotView) child;

            slot.setOnClickListener(v -> {
                if (!slot.isBookable()) {
                    snack(getString(R.string.ds_slot_unavailable,
                            slot.getLabel(), slot.getStatusName().toLowerCase()));
                    return;
                }
                clearSlotSelection();
                slot.setSlotSelected(true);
                snack(getString(R.string.ds_slot_selected, slot.getLabel()));
            });
        }
    }

    private void clearSlotSelection() {
        GridLayout grid = binding.gridSlots;
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            if (child instanceof ParkingSlotView) {
                ((ParkingSlotView) child).setSlotSelected(false);
            }
        }
    }

    // ------------------------------------------------------------------ empty + loading

    private void setupEmptyAndLoading() {
        binding.emptyState.setOnActionClickListener(v -> snack(getString(R.string.ds_empty_clicked)));

        binding.btnShowLoading.setOnClickListener(v -> {
            binding.loadingOverlay.show();
            handler.postDelayed(() -> binding.loadingOverlay.hide(), DEMO_LOADING_MS);
        });
    }

    // ------------------------------------------------------------------ bottom nav

    private void setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener(item -> {
            snack(getString(R.string.ds_nav_clicked, item.getTitle()));
            return true;
        });
    }

    // ------------------------------------------------------------------ helpers

    private void snack(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT)
                .setAnchorView(binding.bottomNav)
                .show();
    }

    private int color(@ColorRes int res) {
        return ContextCompat.getColor(this, res);
    }
}
