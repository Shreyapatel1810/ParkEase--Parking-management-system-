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
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.InsetsHelper;
import com.parkease.app.utils.LocationUtils;
import com.parkease.app.utils.NetworkUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * STEP 7 – Explore tab: every active parking place, with live search and filter chips.
 * Filtering happens on the phone, so it is instant and also works offline.
 */
public class ExploreFragment extends Fragment implements LocationAdapter.Listener {

    private static final long LOAD_TIMEOUT_MS = 8000;

    private FragmentExploreBinding binding;
    private LocationRepository locationRepository;
    private FavouriteRepository favouriteRepository;
    private LocationAdapter adapter;

    private final List<Subscription> subscriptions = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private List<ParkingLocation> all = new ArrayList<>();
    private boolean loaded = false;
    private String query = "";
    @Nullable private String pendingQuery;   // set by Home before this view exists
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

        InsetsHelper.statusBarPadding(binding.header.getRoot());
        InsetsHelper.statusBarScrim(binding.statusBarScrim.getRoot());
        binding.header.toolbar.setTitle(R.string.app_name);
        binding.header.tvHeaderTitle.setText(R.string.explore_title);
        binding.header.tvHeaderSubtitle.setText(R.string.explore_subtitle);

        adapter = new LocationAdapter(LocationAdapter.Mode.CARD_VERTICAL, this);
        binding.rvLocations.setAdapter(adapter);
        binding.rvLocations.addItemDecoration(new SpaceItemDecoration(
                getResources().getDimensionPixelSize(R.dimen.card_gap), false));

        binding.searchBar.setOnQueryChangeListener(q -> {
            query = q;
            render();
        });
        binding.searchBar.setOnSearchListener(q -> {
            query = q;
            render();
        });
        binding.chipGroup.setOnCheckedStateChangeListener((group, ids) -> render());

        if (pendingQuery != null) {
            binding.searchBar.setQuery(pendingQuery);
            query = pendingQuery;
            pendingQuery = null;
        }
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
        binding.searchBar.setQuery(q);
        query = q;
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

    // ------------------------------------------------------------------ filtering

    private void render() {
        if (binding == null || !loaded) return;
        boolean onlyFree = binding.chipAvailable.isChecked();
        boolean car = binding.chipCar.isChecked();
        boolean bike = binding.chipBike.isChecked();
        boolean ev = binding.chipEv.isChecked();
        boolean covered = binding.chipCovered.isChecked();

        List<ParkingLocation> result = new ArrayList<>();
        for (ParkingLocation p : all) {
            if (!p.matches(query)) continue;
            if (onlyFree && p.getAvailableSlots() <= 0) continue;
            if (car && !p.acceptsCars()) continue;
            if (bike && !p.acceptsBikes()) continue;
            if (ev && !p.hasAmenity(Constants.AMENITY_EV)) continue;
            if (covered && !p.hasAmenity(Constants.AMENITY_COVERED)) continue;
            result.add(p);
        }
        LocationUtils.sortForUser(result, userLocation);
        adapter.submitList(result);
        binding.tvResultCount.setText(getString(R.string.explore_results, result.size()));

        if (result.isEmpty()) {
            boolean filtered = !query.isEmpty() || !binding.chipGroup.getCheckedChipIds().isEmpty();
            if (filtered) {
                binding.stateView.showEmpty(R.drawable.ic_search, getString(R.string.explore_no_results_title),
                        getString(R.string.explore_no_results_msg), getString(R.string.explore_clear_filters),
                        v -> clearFilters());
            } else {
                binding.stateView.showEmpty(R.drawable.ic_location, getString(R.string.home_no_locations_title),
                        getString(R.string.home_no_locations_msg), null, null);
            }
        } else {
            binding.stateView.hide();
        }
    }

    private void clearFilters() {
        binding.chipGroup.clearCheck();
        binding.searchBar.setQuery("");
        query = "";
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
