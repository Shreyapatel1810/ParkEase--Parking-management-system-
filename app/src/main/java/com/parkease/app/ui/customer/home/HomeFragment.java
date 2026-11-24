package com.parkease.app.ui.customer.home;

import android.Manifest;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;
import com.parkease.app.R;
import com.parkease.app.adapters.BookingAdapter;
import com.parkease.app.adapters.LocationAdapter;
import com.parkease.app.adapters.SpaceItemDecoration;
import com.parkease.app.databinding.FragmentHomeBinding;
import com.parkease.app.models.AppNotification;
import com.parkease.app.models.Booking;
import com.parkease.app.models.ParkingLocation;
import com.parkease.app.models.Promotion;
import com.parkease.app.models.User;
import com.parkease.app.repository.BookingRepository;
import com.parkease.app.repository.DataCallback;
import com.parkease.app.repository.FavouriteRepository;
import com.parkease.app.repository.LocationRepository;
import com.parkease.app.repository.NotificationRepository;
import com.parkease.app.repository.PromotionRepository;
import com.parkease.app.repository.Subscription;
import com.parkease.app.repository.UserRepository;
import com.parkease.app.ui.customer.CustomerNavHost;
import com.parkease.app.utils.FormatUtils;
import com.parkease.app.utils.ImageUtils;
import com.parkease.app.utils.InsetsHelper;
import com.parkease.app.utils.LocationUtils;
import com.parkease.app.utils.NetworkUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * STEP 7 – Customer Home dashboard.
 *
 * Live Firebase data (updates by itself while the screen is visible):
 *   locations → stats, "Nearby", favourites row     bookings → upcoming booking card
 *   favourites → hearts + favourites row            promotions → offer banner
 *   notifications → red dot on the bell
 * SQLite: "Recently visited".  Pull down to reload everything.
 */
public class HomeFragment extends Fragment implements LocationAdapter.Listener {

    private static final long LOAD_TIMEOUT_MS = 8000;
    private static final long TICK_MS = 60_000;     // re-check "upcoming" every minute

    private FragmentHomeBinding binding;

    private LocationRepository locationRepository;
    private BookingRepository bookingRepository;
    private FavouriteRepository favouriteRepository;
    private PromotionRepository promotionRepository;
    private NotificationRepository notificationRepository;
    private UserRepository userRepository;

    private final List<Subscription> subscriptions = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private LocationAdapter nearbyAdapter;
    private LocationAdapter favouritesAdapter;
    private LocationAdapter recentAdapter;

    // Latest data received
    private List<ParkingLocation> locations = new ArrayList<>();
    private Set<String> favouriteIds = new HashSet<>();
    private List<Booking> bookings = new ArrayList<>();
    private boolean locationsLoaded = false;
    @Nullable private Location userLocation;

    private final ActivityResultLauncher<String> locationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted && binding != null) {
                    Snackbar.make(binding.getRoot(), R.string.location_permission_denied, Snackbar.LENGTH_LONG).show();
                }
                refreshUserLocation();
            });

    private final Runnable loadTimeout = () -> {
        if (binding != null && !locationsLoaded) {
            binding.stateNearby.showError(getString(NetworkUtils.isOnline(requireContext())
                    ? R.string.err_network : R.string.err_no_internet), v -> restartSubscriptions());
            binding.swipeRefresh.setRefreshing(false);
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            renderUpcoming();                       // a booking may have just ended
            handler.postDelayed(this, TICK_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        locationRepository = new LocationRepository(requireContext());
        bookingRepository = new BookingRepository(requireContext());
        favouriteRepository = new FavouriteRepository(requireContext());
        promotionRepository = new PromotionRepository(requireContext());
        notificationRepository = new NotificationRepository(requireContext());
        userRepository = new UserRepository(requireContext());

        InsetsHelper.statusBarPadding(binding.header);
        InsetsHelper.statusBarScrim(binding.statusBarScrim.getRoot());

        setupSections();
        setupLists();
        setupClicks();

        binding.swipeRefresh.setColorSchemeResources(R.color.brand_secondary);
        binding.swipeRefresh.setOnRefreshListener(this::restartSubscriptions);
    }

    @Override
    public void onStart() {
        super.onStart();
        showHeader();
        refreshUserLocation();
        renderRecent();
        startSubscriptions();
        handler.postDelayed(tick, TICK_MS);
    }

    @Override
    public void onStop() {
        stopSubscriptions();
        handler.removeCallbacksAndMessages(null);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    // ================================================================== setup

    private void setupSections() {
        binding.sectionUpcoming.tvSectionTitle.setText(R.string.home_upcoming);
        binding.sectionUpcoming.btnSectionAction.setText(R.string.see_all);
        binding.sectionUpcoming.btnSectionAction.setOnClickListener(v -> host().openTab(R.id.nav_bookings));

        binding.sectionNearby.btnSectionAction.setText(R.string.see_all);
        binding.sectionNearby.btnSectionAction.setOnClickListener(v -> host().openExplore(null));

        binding.sectionFavourites.tvSectionTitle.setText(R.string.home_favourites);
        binding.sectionFavourites.btnSectionAction.setText(R.string.see_all);
        binding.sectionFavourites.btnSectionAction.setOnClickListener(v -> host().openTab(R.id.nav_favourites));

        binding.sectionRecent.tvSectionTitle.setText(R.string.home_recent);
        binding.sectionRecent.btnSectionAction.setVisibility(View.GONE);
    }

    private void setupLists() {
        int gap = getResources().getDimensionPixelSize(R.dimen.card_gap);

        nearbyAdapter = new LocationAdapter(LocationAdapter.Mode.CARD_HORIZONTAL, this);
        binding.rvNearby.setAdapter(nearbyAdapter);
        binding.rvNearby.addItemDecoration(new SpaceItemDecoration(gap, true));

        favouritesAdapter = new LocationAdapter(LocationAdapter.Mode.COMPACT, this);
        binding.rvFavourites.setAdapter(favouritesAdapter);
        binding.rvFavourites.addItemDecoration(new SpaceItemDecoration(gap, true));

        recentAdapter = new LocationAdapter(LocationAdapter.Mode.COMPACT, this);
        binding.rvRecent.setAdapter(recentAdapter);
        binding.rvRecent.addItemDecoration(new SpaceItemDecoration(gap, true));
    }

    private void setupClicks() {
        binding.btnNotifications.setOnClickListener(v -> host().openNotifications());
        binding.avatar.setOnClickListener(v -> host().openTab(R.id.nav_profile));
        binding.searchBar.setOnSearchListener(query -> host().openExplore(query));
        binding.searchBar.setOnFilterClickListener(v -> host().openExplore(null));
        binding.btnFindParking.setOnClickListener(v -> host().openExplore(null));
        binding.upcomingCard.card.setOnClickListener(v -> host().openTab(R.id.nav_bookings));
        binding.btnUseLocation.setOnClickListener(v ->
                locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION));
    }

    // ================================================================== Firebase listeners

    private void startSubscriptions() {
        locationsLoaded = false;
        if (locations.isEmpty()) binding.stateNearby.showLoading();
        handler.postDelayed(loadTimeout, LOAD_TIMEOUT_MS);

        subscriptions.add(locationRepository.observeActiveLocations(new DataCallback<List<ParkingLocation>>() {
            @Override
            public void onSuccess(List<ParkingLocation> data) {
                locationsLoaded = true;
                handler.removeCallbacks(loadTimeout);
                locations = data;
                if (binding == null) return;
                binding.swipeRefresh.setRefreshing(false);
                renderLocations();
            }

            @Override
            public void onError(@NonNull String message) {
                locationsLoaded = true;
                handler.removeCallbacks(loadTimeout);
                if (binding == null) return;
                binding.swipeRefresh.setRefreshing(false);
                binding.stateNearby.showError(message, v -> restartSubscriptions());
            }
        }));

        subscriptions.add(favouriteRepository.observeFavouriteIds(new DataCallback<Set<String>>() {
            @Override
            public void onSuccess(Set<String> ids) {
                favouriteIds = ids;
                if (binding != null) renderLocations();
            }

            @Override
            public void onError(@NonNull String message) { /* hearts simply stay empty */ }
        }));

        subscriptions.add(bookingRepository.observeMyBookings(new DataCallback<List<Booking>>() {
            @Override
            public void onSuccess(List<Booking> data) {
                bookings = data;
                if (binding != null) renderUpcoming();
            }

            @Override
            public void onError(@NonNull String message) {
                bookings = new ArrayList<>();
                if (binding != null) renderUpcoming();
            }
        }));

        subscriptions.add(promotionRepository.observeTopPromotion(new DataCallback<Promotion>() {
            @Override
            public void onSuccess(Promotion promo) {
                if (binding != null) renderPromotion(promo);
            }

            @Override
            public void onError(@NonNull String message) {
                if (binding != null) renderPromotion(null);
            }
        }));

        subscriptions.add(notificationRepository.observeMyNotifications(new DataCallback<List<AppNotification>>() {
            @Override
            public void onSuccess(List<AppNotification> list) {
                if (binding == null) return;
                int unread = NotificationRepository.countUnread(list);
                binding.notificationDot.setVisibility(unread > 0 ? View.VISIBLE : View.GONE);
                binding.btnNotifications.setContentDescription(unread > 0
                        ? getString(R.string.cd_unread_notifications, unread)
                        : getString(R.string.cd_notifications));
            }

            @Override
            public void onError(@NonNull String message) { /* no badge */ }
        }));
    }

    private void stopSubscriptions() {
        for (Subscription s : subscriptions) s.cancel();
        subscriptions.clear();
    }

    private void restartSubscriptions() {
        stopSubscriptions();
        handler.removeCallbacks(loadTimeout);
        refreshUserLocation();
        startSubscriptions();
    }

    // ================================================================== rendering

    private void showHeader() {
        User user = userRepository.getCachedProfile();
        String first = user == null ? "" : user.getFirstName();
        binding.tvGreeting.setText(first.isEmpty() ? FormatUtils.greetingPart()
                : getString(R.string.home_hello, FormatUtils.greetingPart(), first));
        ImageUtils.showAvatar(binding.imgAvatar, binding.tvInitials, user);
    }

    private void refreshUserLocation() {
        userLocation = LocationUtils.getLastKnown(requireContext());
        if (binding == null) return;
        binding.btnUseLocation.setVisibility(LocationUtils.hasPermission(requireContext()) ? View.GONE : View.VISIBLE);
        binding.sectionNearby.tvSectionTitle.setText(userLocation != null ? R.string.home_nearby : R.string.home_popular);
        nearbyAdapter.setUserLocation(userLocation);
        if (!locations.isEmpty()) renderLocations();
    }

    /** Stats, Nearby row, Favourites row, and fresh data for the Recent row. */
    private void renderLocations() {
        // ---- stats
        int free = 0, open = 0;
        for (ParkingLocation p : locations) {
            free += Math.max(0, p.getAvailableSlots());
            if (FormatUtils.isOpenNow(p.getOpenTime(), p.getCloseTime())) open++;
        }
        binding.statAvailable.setValue(locationsLoaded ? String.valueOf(free) : "—");
        binding.statAvailable.setSubtitle(getString(R.string.home_stat_across, locations.size()));
        binding.statLocations.setValue(String.valueOf(locations.size()));
        binding.statLocations.setSubtitle(getString(R.string.home_stat_open, open));

        // ---- nearby (nearest first, or most free slots if location is unknown)
        List<ParkingLocation> sorted = new ArrayList<>(locations);
        LocationUtils.sortForUser(sorted, userLocation);
        nearbyAdapter.setFavouriteIds(favouriteIds);
        nearbyAdapter.submitList(sorted.size() > 10 ? sorted.subList(0, 10) : sorted);
        if (sorted.isEmpty()) {
            binding.stateNearby.showEmpty(R.drawable.ic_location, getString(R.string.home_no_locations_title),
                    getString(R.string.home_no_locations_msg), null, null);
        } else {
            binding.stateNearby.hide();
        }

        // ---- favourites
        List<ParkingLocation> favs = new ArrayList<>();
        for (ParkingLocation p : locations) if (favouriteIds.contains(p.getId())) favs.add(p);
        favouritesAdapter.setFavouriteIds(favouriteIds);
        favouritesAdapter.submitList(favs);
        binding.rvFavourites.setVisibility(favs.isEmpty() ? View.GONE : View.VISIBLE);
        binding.tvNoFavourites.setVisibility(favs.isEmpty() ? View.VISIBLE : View.GONE);

        renderRecent();
    }

    /** Recently visited from SQLite; replaced by live Firebase data when available. */
    private void renderRecent() {
        if (binding == null) return;
        Map<String, ParkingLocation> live = new HashMap<>();
        for (ParkingLocation p : locations) live.put(p.getId(), p);

        List<ParkingLocation> recent = new ArrayList<>();
        for (ParkingLocation saved : locationRepository.getRecentlyVisited()) {
            ParkingLocation fresh = live.get(saved.getId());
            if (fresh != null) recent.add(fresh);
            else if (!locationsLoaded || locations.isEmpty()) recent.add(saved);  // offline: show saved copy
            // else: location was removed by the admin → skip it
        }
        recentAdapter.setFavouriteIds(favouriteIds);
        recentAdapter.submitList(recent);
        binding.recentGroup.setVisibility(recent.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void renderUpcoming() {
        if (binding == null) return;
        Booking next = BookingRepository.findNextUpcoming(bookings);
        if (next == null) {
            binding.upcomingCard.getRoot().setVisibility(View.GONE);
            binding.noUpcomingCard.setVisibility(View.VISIBLE);
            binding.sectionUpcoming.btnSectionAction.setVisibility(bookings.isEmpty() ? View.GONE : View.VISIBLE);
        } else {
            BookingAdapter.bind(binding.upcomingCard, next);
            binding.upcomingCard.getRoot().setVisibility(View.VISIBLE);
            binding.noUpcomingCard.setVisibility(View.GONE);
            binding.sectionUpcoming.btnSectionAction.setVisibility(View.VISIBLE);
        }
    }

    private void renderPromotion(@Nullable Promotion promo) {
        if (promo == null) {
            binding.tvPromoTitle.setText(R.string.promo_fallback_title);
            binding.tvPromoMessage.setText(R.string.promo_fallback_message);
            binding.tvPromoCode.setVisibility(View.GONE);
            return;
        }
        binding.tvPromoTitle.setText(promo.getTitle());
        binding.tvPromoMessage.setText(promo.getMessage());
        boolean hasCode = promo.getCode() != null && !promo.getCode().trim().isEmpty();
        binding.tvPromoCode.setVisibility(hasCode ? View.VISIBLE : View.GONE);
        if (hasCode) binding.tvPromoCode.setText(getString(R.string.promo_code, promo.getCode()));
    }

    // ================================================================== list clicks

    @Override
    public void onLocationClick(@NonNull ParkingLocation location) {
        host().openLocation(location.getId());
    }

    @Override
    public void onFavouriteClick(@NonNull ParkingLocation location, boolean makeFavourite) {
        favouriteRepository.setFavourite(location.getId(), makeFavourite, new DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) { /* the live listener updates the hearts */ }

            @Override
            public void onError(@NonNull String message) {
                if (binding != null) Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            }
        });
        if (binding != null) {
            Snackbar.make(binding.getRoot(), makeFavourite ? R.string.favourite_added : R.string.favourite_removed,
                    Snackbar.LENGTH_SHORT).show();
        }
    }

    private CustomerNavHost host() {
        return (CustomerNavHost) requireActivity();
    }
}
