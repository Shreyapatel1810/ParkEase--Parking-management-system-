package com.parkease.app.ui.customer.search;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.parkease.app.R;
import com.parkease.app.databinding.BottomSheetSortFilterBinding;
import com.parkease.app.utils.FormatUtils;

/**
 * "Sort & filter" sheet that slides up from the bottom of Explore.
 * It receives the current choices, lets the user change them, and sends them back with
 * {@code setFragmentResult(REQUEST_KEY, bundle)} when "Show results" is tapped.
 */
public class SortFilterBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "SortFilterBottomSheet";
    public static final String REQUEST_KEY = "sort_filter_result";

    /** Slider's last position means "no price limit". */
    private static final float SLIDER_ANY = 100f;

    private BottomSheetSortFilterBinding binding;

    @NonNull
    public static SortFilterBottomSheet newInstance(@NonNull SearchFilters current) {
        SortFilterBottomSheet sheet = new SortFilterBottomSheet();
        sheet.setArguments(current.toBundle());
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetSortFilterBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        SearchFilters f = new SearchFilters();
        if (getArguments() != null) f.readFrom(getArguments());
        if (savedInstanceState == null) show(f);

        binding.sliderPrice.addOnChangeListener((slider, value, fromUser) -> updatePriceLabel(value));
        updatePriceLabel(binding.sliderPrice.getValue());

        binding.btnReset.setOnClickListener(v -> show(new SearchFilters()));
        binding.btnApply.setOnClickListener(v -> {
            getParentFragmentManager().setFragmentResult(REQUEST_KEY, read().toBundle());
            dismiss();
        });
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    /** Puts the choices on screen. */
    private void show(@NonNull SearchFilters f) {
        int sortId;
        switch (f.sort) {
            case PRICE_LOW: sortId = R.id.sortPriceLow; break;
            case PRICE_HIGH: sortId = R.id.sortPriceHigh; break;
            case MOST_AVAILABLE: sortId = R.id.sortAvailable; break;
            case TOP_RATED: sortId = R.id.sortRating; break;
            default: sortId = R.id.sortRecommended; break;
        }
        binding.groupSort.check(sortId);
        binding.groupVehicle.check(f.vehicle == SearchFilters.Vehicle.CAR ? R.id.vehicleCar
                : f.vehicle == SearchFilters.Vehicle.BIKE ? R.id.vehicleBike : R.id.vehicleAny);
        float value = f.maxPrice == SearchFilters.ANY_PRICE ? SLIDER_ANY
                : Math.max(10f, Math.min(SLIDER_ANY, Math.round(f.maxPrice / 10f) * 10f));
        binding.sliderPrice.setValue(value);
        binding.chipAvailable.setChecked(f.onlyAvailable);
        binding.chipEv.setChecked(f.ev);
        binding.chipCovered.setChecked(f.covered);
    }

    /** Reads the choices from the screen. */
    @NonNull
    private SearchFilters read() {
        SearchFilters f = new SearchFilters();
        int sortId = binding.groupSort.getCheckedChipId();
        f.sort = sortId == R.id.sortPriceLow ? SearchFilters.Sort.PRICE_LOW
                : sortId == R.id.sortPriceHigh ? SearchFilters.Sort.PRICE_HIGH
                : sortId == R.id.sortAvailable ? SearchFilters.Sort.MOST_AVAILABLE
                : sortId == R.id.sortRating ? SearchFilters.Sort.TOP_RATED
                : SearchFilters.Sort.RECOMMENDED;
        int vehicleId = binding.groupVehicle.getCheckedChipId();
        f.vehicle = vehicleId == R.id.vehicleCar ? SearchFilters.Vehicle.CAR
                : vehicleId == R.id.vehicleBike ? SearchFilters.Vehicle.BIKE
                : SearchFilters.Vehicle.ANY;
        float price = binding.sliderPrice.getValue();
        f.maxPrice = price >= SLIDER_ANY ? SearchFilters.ANY_PRICE : Math.round(price);
        f.onlyAvailable = binding.chipAvailable.isChecked();
        f.ev = binding.chipEv.isChecked();
        f.covered = binding.chipCovered.isChecked();
        return f;
    }

    private void updatePriceLabel(float value) {
        binding.tvPriceValue.setText(value >= SLIDER_ANY
                ? getString(R.string.filter_price_any)
                : getString(R.string.filter_price_upto, FormatUtils.money(value)));
    }
}
