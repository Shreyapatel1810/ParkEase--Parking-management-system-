package com.parkease.app.ui.customer.booking;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.tabs.TabLayout;
import com.parkease.app.R;
import com.parkease.app.adapters.BookingAdapter;
import com.parkease.app.adapters.SpaceItemDecoration;
import com.parkease.app.databinding.FragmentMyBookingsBinding;
import com.parkease.app.models.Booking;
import com.parkease.app.repository.BookingRepository;
import com.parkease.app.repository.DataCallback;
import com.parkease.app.repository.Subscription;
import com.parkease.app.ui.customer.CustomerNavHost;
import com.parkease.app.utils.InsetsHelper;
import com.parkease.app.utils.NetworkUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * STEP 7 – My Bookings tab, read live from Firebase.
 * Tabs: Upcoming (confirmed / parked, not ended), Past (completed / expired / ended), Cancelled.
 * Tapping a booking opens its parking place (booking details + QR ticket come in the booking module).
 */
public class MyBookingsFragment extends Fragment {

    private static final long LOAD_TIMEOUT_MS = 8000;
    private static final String[] TABS = {
            BookingRepository.TAB_UPCOMING, BookingRepository.TAB_PAST, BookingRepository.TAB_CANCELLED};

    private FragmentMyBookingsBinding binding;
    private BookingRepository bookingRepository;
    private BookingAdapter adapter;
    private Subscription subscription = Subscription.EMPTY;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private List<Booking> all = new ArrayList<>();
    private boolean loaded = false;

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
        binding = FragmentMyBookingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        bookingRepository = new BookingRepository(requireContext());

        InsetsHelper.statusBarPadding(binding.header.getRoot());
        InsetsHelper.statusBarScrim(binding.statusBarScrim.getRoot());
        binding.header.toolbar.setTitle(R.string.app_name);
        binding.header.tvHeaderTitle.setText(R.string.bookings_title);
        binding.header.tvHeaderSubtitle.setText(R.string.bookings_subtitle);

        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.bookings_tab_upcoming));
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.bookings_tab_past));
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.bookings_tab_cancelled));
        binding.tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { render(); }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });

        adapter = new BookingAdapter(booking -> {
            if (booking.getLocationId() != null) {
                ((CustomerNavHost) requireActivity()).openLocation(booking.getLocationId());
            }
        });
        binding.rvBookings.setAdapter(adapter);
        binding.rvBookings.addItemDecoration(new SpaceItemDecoration(
                getResources().getDimensionPixelSize(R.dimen.card_gap), false));
    }

    @Override
    public void onStart() {
        super.onStart();
        start();
    }

    @Override
    public void onStop() {
        handler.removeCallbacksAndMessages(null);
        subscription.cancel();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void start() {
        loaded = false;
        if (all.isEmpty()) binding.stateView.showLoading();
        handler.postDelayed(loadTimeout, LOAD_TIMEOUT_MS);
        subscription = bookingRepository.observeMyBookings(new DataCallback<List<Booking>>() {
            @Override
            public void onSuccess(List<Booking> data) {
                loaded = true;
                handler.removeCallbacks(loadTimeout);
                all = data;
                render();
            }

            @Override
            public void onError(@NonNull String message) {
                loaded = true;
                handler.removeCallbacks(loadTimeout);
                if (binding != null) binding.stateView.showError(message, v -> restart());
            }
        });
    }

    private void restart() {
        handler.removeCallbacksAndMessages(null);
        subscription.cancel();
        start();
    }

    private void render() {
        if (binding == null || !loaded) return;
        String tab = TABS[Math.max(0, binding.tabs.getSelectedTabPosition())];
        List<Booking> list = BookingRepository.filter(all, tab);
        adapter.submitList(list);

        if (!list.isEmpty()) {
            binding.stateView.hide();
            return;
        }
        int title, message;
        switch (tab) {
            case BookingRepository.TAB_UPCOMING:
                title = R.string.bookings_empty_upcoming; message = R.string.bookings_empty_upcoming_msg; break;
            case BookingRepository.TAB_CANCELLED:
                title = R.string.bookings_empty_cancelled; message = R.string.bookings_empty_cancelled_msg; break;
            default:
                title = R.string.bookings_empty_past; message = R.string.bookings_empty_past_msg; break;
        }
        boolean upcoming = BookingRepository.TAB_UPCOMING.equals(tab);
        binding.stateView.showEmpty(R.drawable.ic_bookings, getString(title), getString(message),
                upcoming ? getString(R.string.home_find_parking) : null,
                upcoming ? v -> ((CustomerNavHost) requireActivity()).openExplore(null) : null);
    }
}
