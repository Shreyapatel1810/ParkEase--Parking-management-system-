package com.parkease.app.ui.customer.favourite;

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
import com.parkease.app.databinding.FragmentFavouritesBinding;
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
 * STEP 7 – Favourites tab.
 * Combines two live Firebase lists: my favourite IDs + all locations → my favourite locations.
 * Tapping the heart removes a place (with Undo).
 */
public class FavouritesFragment extends Fragment implements LocationAdapter.Listener {

    private static final long LOAD_TIMEOUT_MS = 8000;

    private FragmentFavouritesBinding binding;
    private LocationRepository locationRepository;
    private FavouriteRepository favouriteRepository;
    private LocationAdapter adapter;

    private final List<Subscription> subscriptions = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private List<ParkingLocation> locations = new ArrayList<>();
    private Set<String> favouriteIds = new HashSet<>();
    private boolean locationsLoaded = false;
    private boolean favouritesLoaded = false;

    private final Runnable loadTimeout = () -> {
        if (binding != null && !(locationsLoaded && favouritesLoaded)) {
            binding.stateView.showError(getString(NetworkUtils.isOnline(requireContext())
                    ? R.string.err_network : R.string.err_no_internet), v -> restart());
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentFavouritesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        locationRepository = new LocationRepository(requireContext());
        favouriteRepository = new FavouriteRepository(requireContext());

        InsetsHelper.statusBarPadding(binding.header.getRoot());
        InsetsHelper.statusBarScrim(binding.statusBarScrim.getRoot());
        binding.header.toolbar.setTitle(R.string.app_name);
        binding.header.tvHeaderTitle.setText(R.string.favourites_title);
        binding.header.tvHeaderSubtitle.setText(R.string.favourites_subtitle);
        // No overlapping card on this screen, so the header needs less bottom space
        binding.header.getRoot().setPaddingRelative(0, binding.header.getRoot().getPaddingTop(), 0,
                getResources().getDimensionPixelSize(R.dimen.space_xl));

        adapter = new LocationAdapter(LocationAdapter.Mode.CARD_VERTICAL, this);
        binding.rvFavourites.setAdapter(adapter);
        binding.rvFavourites.addItemDecoration(new SpaceItemDecoration(
                getResources().getDimensionPixelSize(R.dimen.card_gap), false));
    }

    @Override
    public void onStart() {
        super.onStart();
        adapter.setUserLocation(LocationUtils.getLastKnown(requireContext()));
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

    private void start() {
        locationsLoaded = false;
        favouritesLoaded = false;
        binding.stateView.showLoading();
        handler.postDelayed(loadTimeout, LOAD_TIMEOUT_MS);

        subscriptions.add(locationRepository.observeActiveLocations(new DataCallback<List<ParkingLocation>>() {
            @Override
            public void onSuccess(List<ParkingLocation> data) {
                locations = data;
                locationsLoaded = true;
                render();
            }

            @Override
            public void onError(@NonNull String message) {
                handler.removeCallbacks(loadTimeout);
                if (binding != null) binding.stateView.showError(message, v -> restart());
            }
        }));
        subscriptions.add(favouriteRepository.observeFavouriteIds(new DataCallback<Set<String>>() {
            @Override
            public void onSuccess(Set<String> ids) {
                favouriteIds = ids;
                favouritesLoaded = true;
                render();
            }

            @Override
            public void onError(@NonNull String message) {
                handler.removeCallbacks(loadTimeout);
                if (binding != null) binding.stateView.showError(message, v -> restart());
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

    private void render() {
        if (binding == null || !locationsLoaded || !favouritesLoaded) return;
        handler.removeCallbacks(loadTimeout);

        List<ParkingLocation> favs = new ArrayList<>();
        for (ParkingLocation p : locations) if (favouriteIds.contains(p.getId())) favs.add(p);
        adapter.setFavouriteIds(favouriteIds);
        adapter.submitList(favs);

        if (favs.isEmpty()) {
            binding.stateView.showEmpty(R.drawable.ic_heart_outline, getString(R.string.favourites_empty_title),
                    getString(R.string.favourites_empty_msg), getString(R.string.favourites_explore),
                    v -> ((CustomerNavHost) requireActivity()).openExplore(null));
        } else {
            binding.stateView.hide();
        }
    }

    @Override
    public void onLocationClick(@NonNull ParkingLocation location) {
        ((CustomerNavHost) requireActivity()).openLocation(location.getId());
    }

    @Override
    public void onFavouriteClick(@NonNull ParkingLocation location, boolean makeFavourite) {
        setFavourite(location, makeFavourite);
        if (!makeFavourite && binding != null) {
            Snackbar.make(binding.getRoot(), R.string.favourite_removed, Snackbar.LENGTH_LONG)
                    .setAction(R.string.action_undo, v -> setFavourite(location, true))
                    .show();
        }
    }

    private void setFavourite(@NonNull ParkingLocation location, boolean favourite) {
        favouriteRepository.setFavourite(location.getId(), favourite, new DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) { }

            @Override
            public void onError(@NonNull String message) {
                if (binding != null) Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            }
        });
    }
}
