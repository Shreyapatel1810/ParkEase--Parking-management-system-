package com.parkease.app.ui.customer.search;

import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;
import com.parkease.app.R;
import com.parkease.app.adapters.LocationAdapter;
import com.parkease.app.adapters.SpaceItemDecoration;
import com.parkease.app.databinding.FragmentExploreBinding;
import com.parkease.app.models.ParkingLocation;
import com.parkease.app.repository.DataCallback;
import com.parkease.app.repository.FavouriteRepository;
import com.parkease.app.repository.LocationRepository;
import com.parkease.app.repository.Subscription;
import com.parkease.app.ui.customer.CustomerNavHost;
import com.parkease.app.utils.InsetsHelper;
import com.parkease.app.utils.LocationUtils;
import com.parkease.app.utils.NetworkUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * STEP 7/8 – Explore: search and filter every active parking place (live from Firebase).
 *
 * Search bar  → name, area, city or code
 * Quick chips → Free now, Car, Bike, EV charging, Covered
 * "Sort & filter" sheet → sort order, vehicle type, maximum price, availability, facilities
 * All choices live in one {@link SearchFilters} object; filtering runs on the phone, so it
 * is instant and also works offline.
 */
public class ExploreFragment extends Fragment implements LocationAdapter.Listener {

    private static final long LOAD_TIMEOUT_MS = 8000;
    private static final String KEY_FILTERS = "filters";
    private static final String KEY_QUERY = "query";

    private FragmentExploreBinding binding;
    private LocationRepository locationRepository;
    private FavouriteRepository favouriteRepository;
    private LocationAdapter adapter;

    private final List<Subscription> subscriptions = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final SearchFilters filters = new SearchFilters();
    private List<ParkingLocation> all = new ArrayList<>();
    private boolean loaded = false;
    private boolean syncingChips = false;       // true while chips are updated from code
    @Nullable private String pendingQuery;      // set by Home before this view exists
    @Nullable private Location userLocation;

    private final Runnable loadTimeout = () -> {
        if (binding != null && !loaded) {
            binding.stateView.showError(getString(NetworkUtils.isOnline(requireContext())
                    ? R.string.err_network : R.string.err_no_internet), v -> restart());
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentExploreBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        locationRepository = new LocationRepository(requireContext());
        favouriteRepository = new FavouriteRepository(requireContext());

        if (savedInstanceState != null) {
            Bundle saved = savedInstanceState.getBundle(KEY_FILTERS);
            if (saved != null) filters.readFrom(saved);
            filters.query = savedInstanceState.getString(KEY_QUERY, "");
        }

        InsetsHelper.statusBarPadding(binding.header.getRoot());
        InsetsHelper.statusBarScrim(binding.statusBarScrim.getRoot());
        binding.header.toolbar.setTitle(R.string.app_name);
        binding.header.tvHeaderTitle.setText(R.string.explore_title);
        binding.header.tvHeaderSubtitle.setText(R.string.explore_subtitle);

        adapter = new LocationAdapter(LocationAdapter.Mode.CARD_VERTICAL, this);
        binding.rvLocations.setAdapter(adapter);
        binding.rvLocations.addItemDecoration(new SpaceItemDecoration(
                getResources().getDimensionPixelSize(R.dimen.card_gap), false));

        // Search text
        binding.searchBar.setOnQueryChangeListener(q -> {
            filters.query = q;
            render();
        });
        binding.searchBar.setOnSearchListener(q -> {
            filters.query = q;
            render();
        });

        // Quick chips
        binding.chipCar.setOnCheckedChangeListener((c, checked) -> onVehicleChip(SearchFilters.Vehicle.CAR, checked));
        binding.chipBike.setOnCheckedChangeListener((c, checked) -> onVehicleChip(SearchFilters.Vehicle.BIKE, checked));
        binding.chipAvailable.setOnCheckedChangeListener((c, checked) -> {
            if (syncingChips) return;
            filters.onlyAvailable = checked;
            render();
        });
        binding.chipEv.setOnCheckedChangeListener((c, checked) -> {
            if (syncingChips) return;
            filters.ev = checked;
            render();
        });
        binding.chipCovered.setOnCheckedChangeListener((c, checked) -> {
            if (syncingChips) return;
            filters.covered = checked;
            render();
        });

        // Sort & filter sheet
        binding.chipSortFilter.setOnClickListener(v -> openSortFilter());
        getChildFragmentManager().setFragmentResultListener(SortFilterBottomSheet.REQUEST_KEY,
                getViewLifecycleOwner(), (key, result) -> {
                    filters.readFrom(result);
                    syncChips();
                    render();
                });

        if (pendingQuery != null) {
            filters.query = pendingQuery;
            pendingQuery = null;
        }
        if (!filters.query.isEmpty()) binding.searchBar.setQuery(filters.query);
        syncChips();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBundle(KEY_FILTERS, filters.toBundle());
        outState.putString(KEY_QUERY, filters.query);
    }

    @Override
    public void onStart() {
        super.onStart();
        userLocation = LocationUtils.getLastKnown(requireContext());
        adapter.setUserLocation(userLocation);
        start();
    }

    @Override
    public void onStop() {
        stop();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    /** Called by the main screen (e.g. from the Home search bar). */
    public void setQuery(@Nullable String text) {
        String q = text == null ? "" : text;
        if (binding == null) {
            pendingQuery = q;
            return;
        }
        filters.query = q;
        binding.searchBar.setQuery(q);
        render();
    }

    // ------------------------------------------------------------------ data

    private void start() {
        loaded = false;
        if (all.isEmpty()) binding.stateView.showLoading();
        handler.postDelayed(loadTimeout, LOAD_TIMEOUT_MS);

        subscriptions.add(locationRepository.observeActiveLocations(new DataCallback<List<ParkingLocation>>() {
            @Override
            public void onSuccess(List<ParkingLocation> data) {
                loaded = true;
                handler.removeCallbacks(loadTimeout);
                all = data;
                if (binding != null) render();
            }

            @Override
            public void onError(@NonNull String message) {
                loaded = true;
                handler.removeCallbacks(loadTimeout);
                if (binding != null) binding.stateView.showError(message, v -> restart());
            }
        }));

        subscriptions.add(favouriteRepository.observeFavouriteIds(new DataCallback<Set<String>>() {
            @Override
            public void onSuccess(Set<String> ids) {
                adapter.setFavouriteIds(ids);
            }

            @Override
            public void onError(@NonNull String message) {
                adapter.setFavouriteIds(new HashSet<>());
            }
        }));
    }

    private void stop() {
        handler.removeCallbacksAndMessages(null);
        for (Subscription s : subscriptions) s.cancel();
        subscriptions.clear();
    }

    private void restart() {
        stop();
        start();
    }

    // ------------------------------------------------------------------ filters

    /** Car and Bike exclude each other: choosing one un-ticks the other. */
    private void onVehicleChip(@NonNull SearchFilters.Vehicle vehicle, boolean checked) {
        if (syncingChips) return;
        if (checked) {
            filters.vehicle = vehicle;
        } else if (filters.vehicle == vehicle) {
            filters.vehicle = SearchFilters.Vehicle.ANY;
        }
        syncChips();
        render();
    }

    /** Makes the chips match the filters (without triggering their listeners). */
    private void syncChips() {
        syncingChips = true;
        binding.chipCar.setChecked(filters.vehicle == SearchFilters.Vehicle.CAR);
        binding.chipBike.setChecked(filters.vehicle == SearchFilters.Vehicle.BIKE);
        binding.chipAvailable.setChecked(filters.onlyAvailable);
        binding.chipEv.setChecked(filters.ev);
        binding.chipCovered.setChecked(filters.covered);
        int count = filters.activeFilterCount() + (filters.sort != SearchFilters.Sort.RECOMMENDED ? 1 : 0);
        binding.chipSortFilter.setText(count > 0
                ? getString(R.string.sort_filter_button_count, count)
                : getString(R.string.sort_filter_button));
        syncingChips = false;
    }

    private void openSortFilter() {
        if (getChildFragmentManager().findFragmentByTag(SortFilterBottomSheet.TAG) != null) return;
        SortFilterBottomSheet.newInstance(filters).show(getChildFragmentManager(), SortFilterBottomSheet.TAG);
    }

    private void render() {
        if (binding == null || !loaded) return;
        List<ParkingLocation> result = filters.apply(all, userLocation);
        adapter.submitList(result);

        binding.tvResultCount.setText(filters.sort == SearchFilters.Sort.RECOMMENDED
                ? getString(R.string.explore_results, result.size())
                : getString(R.string.explore_results_sorted, result.size(), getString(filters.sort.label)));

        if (!result.isEmpty()) {
            binding.stateView.hide();
        } else if (!filters.query.isEmpty() || filters.activeFilterCount() > 0) {
            binding.stateView.showEmpty(R.drawable.ic_search, getString(R.string.explore_no_results_title),
                    getString(R.string.explore_no_results_msg), getString(R.string.explore_clear_filters),
                    v -> clearFilters());
        } else {
            binding.stateView.showEmpty(R.drawable.ic_location, getString(R.string.home_no_locations_title),
                    getString(R.string.home_no_locations_msg), null, null);
        }
    }

    private void clearFilters() {
        filters.reset();
        binding.searchBar.setQuery("");
        syncChips();
        render();
    }

    // ------------------------------------------------------------------ clicks

    @Override
    public void onLocationClick(@NonNull ParkingLocation location) {
        ((CustomerNavHost) requireActivity()).openLocation(location.getId());
    }

    @Override
    public void onFavouriteClick(@NonNull ParkingLocation location, boolean makeFavourite) {
        favouriteRepository.setFavourite(location.getId(), makeFavourite, new DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) { }

            @Override
            public void onError(@NonNull String message) {
                if (binding != null) Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            }
        });
        Snackbar.make(binding.getRoot(), makeFavourite ? R.string.favourite_added : R.string.favourite_removed,
                Snackbar.LENGTH_SHORT).show();
    }
}
