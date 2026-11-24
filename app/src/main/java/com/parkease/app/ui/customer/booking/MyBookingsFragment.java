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

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.parkease.app.R;
import com.parkease.app.adapters.ActiveBookingAdapter;
import com.parkease.app.adapters.MyBookingAdapter;
import com.parkease.app.adapters.SpaceItemDecoration;
import com.parkease.app.databinding.FragmentMyBookingsBinding;
import com.parkease.app.models.Booking;
import com.parkease.app.models.CancellationPolicy;
import com.parkease.app.models.Payment;
import com.parkease.app.repository.BookingRepository;
import com.parkease.app.repository.DataCallback;
import com.parkease.app.repository.PaymentRepository;
import com.parkease.app.repository.Subscription;
import com.parkease.app.ui.customer.CustomerNavHost;
import com.parkease.app.ui.customer.payment.PaymentActivity;
import com.parkease.app.ui.customer.payment.PaymentResultActivity;
import com.parkease.app.ui.customer.ticket.TicketActivity;
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.InsetsHelper;
import com.parkease.app.utils.NetworkUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * STEP 14 – My Bookings, live from Firebase.
 *
 * Tabs:
 *  - Upcoming: confirmed bookings that haven't started, and holds waiting for payment
 *  - Active:   vehicle parked, or booking time running now (live timer, QR ticket)
 *  - History:  completed / cancelled / expired, with search and status filter chips
 *
 * Each card shows booking ID, parking name, slot, vehicle, date, start–end time, payment
 * status and booking status. Actions: details (tap), QR ticket, cancel, receipt.
 * Bookings and payments are two live Firebase listeners; a timer re-sorts the tabs as time
 * passes (e.g. an upcoming booking moves to Active when it starts).
 */
public class MyBookingsFragment extends Fragment implements MyBookingAdapter.Listener {

    private static final long LOAD_TIMEOUT_MS = 8000;
    private static final long TICK_MS = 30_000;
    private static final String[] TABS = {
            BookingRepository.TAB_UPCOMING, BookingRepository.TAB_ACTIVE, BookingRepository.TAB_HISTORY};
    private static final int[] TAB_NAMES = {
            R.string.bookings_tab_upcoming, R.string.bookings_tab_active, R.string.bookings_tab_history};
    private static final String STATE_TAB = "tab";

    private FragmentMyBookingsBinding binding;
    private BookingRepository bookingRepository;
    private PaymentRepository paymentRepository;
    private MyBookingAdapter cardAdapter;          // Upcoming + History
    private ActiveBookingAdapter activeAdapter;    // Active
    private Subscription bookingSub = Subscription.EMPTY;
    private Subscription paymentSub = Subscription.EMPTY;
    private CancellationPolicy policy = new CancellationPolicy();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private List<Booking> all = new ArrayList<>();
    private boolean loaded = false;
    private String query = "";
    private String historyStatus = BookingRepository.HISTORY_ALL;

    private final Runnable loadTimeout = () -> {
        if (binding != null && !loaded) {
            binding.swipeRefresh.setRefreshing(false);
            binding.stateView.showError(getString(NetworkUtils.isOnline(requireContext())
                    ? R.string.err_network : R.string.err_no_internet), v -> restart());
        }
    };

    /** Every 30 s: move bookings between tabs as time passes and update the Active timers. */
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            render();
            handler.postDelayed(this, TICK_MS);
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
        paymentRepository = new PaymentRepository(requireContext());

        InsetsHelper.statusBarPadding(binding.header.getRoot());
        InsetsHelper.statusBarScrim(binding.statusBarScrim.getRoot());
        binding.header.toolbar.setTitle(R.string.app_name);
        binding.header.tvHeaderTitle.setText(R.string.bookings_title);
        binding.header.tvHeaderSubtitle.setText(R.string.bookings_subtitle);

        for (int name : TAB_NAMES) binding.tabs.addTab(binding.tabs.newTab().setText(name));
        int tab = savedInstanceState == null ? 0 : savedInstanceState.getInt(STATE_TAB, 0);
        TabLayout.Tab saved = binding.tabs.getTabAt(tab);
        if (saved != null) saved.select();
        binding.tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab t) { render(); }
            @Override public void onTabUnselected(TabLayout.Tab t) { }
            @Override public void onTabReselected(TabLayout.Tab t) { binding.rvBookings.smoothScrollToPosition(0); }
        });

        cardAdapter = new MyBookingAdapter(this);
        activeAdapter = new ActiveBookingAdapter(this);
        binding.rvBookings.addItemDecoration(new SpaceItemDecoration(
                getResources().getDimensionPixelSize(R.dimen.card_gap), false));

        // History search + filter
        binding.searchBar.setOnQueryChangeListener(q -> {
            query = q;
            render();
        });
        binding.chipsStatus.setOnCheckedStateChangeListener((group, ids) -> {
            int id = ids.isEmpty() ? R.id.chipAll : ids.get(0);
            historyStatus = id == R.id.chipCompleted ? Constants.BOOKING_COMPLETED
                    : id == R.id.chipCancelled ? Constants.BOOKING_CANCELLED
                    : id == R.id.chipExpired ? Constants.BOOKING_EXPIRED
                    : BookingRepository.HISTORY_ALL;
            render();
        });

        binding.swipeRefresh.setColorSchemeResources(R.color.brand_secondary);
        binding.swipeRefresh.setOnRefreshListener(this::restart);

        bookingRepository.loadCancellationPolicy(new DataCallback<CancellationPolicy>() {
            @Override public void onSuccess(CancellationPolicy p) { policy = p; }
            @Override public void onError(@NonNull String message) { }
        });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (binding != null) outState.putInt(STATE_TAB, binding.tabs.getSelectedTabPosition());
    }

    @Override
    public void onStart() {
        super.onStart();
        start();
        handler.postDelayed(ticker, TICK_MS);
    }

    @Override
    public void onStop() {
        handler.removeCallbacksAndMessages(null);
        bookingSub.cancel();
        paymentSub.cancel();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    // ================================================================== loading

    private void start() {
        loaded = false;
        if (all.isEmpty()) binding.stateView.showLoading();
        handler.postDelayed(loadTimeout, LOAD_TIMEOUT_MS);

        bookingSub = bookingRepository.observeMyBookings(new DataCallback<List<Booking>>() {
            @Override
            public void onSuccess(List<Booking> data) {
                loaded = true;
                handler.removeCallbacks(loadTimeout);
                all = data;
                bookingRepository.expireStaleBookings(data);   // ran-out holds & no-shows → EXPIRED
                if (binding != null) binding.swipeRefresh.setRefreshing(false);
                render();
            }

            @Override
            public void onError(@NonNull String message) {
                loaded = true;
                handler.removeCallbacks(loadTimeout);
                if (binding == null) return;
                binding.swipeRefresh.setRefreshing(false);
                binding.stateView.showError(message, v -> restart());
            }
        });

        // Payment status for every card (one listener for all my payments)
        paymentSub = paymentRepository.observeMyPayments(new DataCallback<List<Payment>>() {
            @Override
            public void onSuccess(List<Payment> list) {
                Map<String, Payment> byId = new HashMap<>();
                for (Payment p : list) byId.put(p.getTransactionId(), p);
                cardAdapter.setPayments(byId);
                activeAdapter.setPayments(byId);
            }

            @Override
            public void onError(@NonNull String message) {
                // Cards still show the booking; the payment badge just stays hidden
            }
        });
    }

    private void restart() {
        handler.removeCallbacks(loadTimeout);
        bookingSub.cancel();
        paymentSub.cancel();
        start();
    }

    // ================================================================== screen

    private void render() {
        if (binding == null || !loaded) return;
        updateTabCounts();
        String tab = TABS[Math.max(0, binding.tabs.getSelectedTabPosition())];
        boolean history = BookingRepository.TAB_HISTORY.equals(tab);
        boolean active = BookingRepository.TAB_ACTIVE.equals(tab);
        binding.historyControls.setVisibility(history ? View.VISIBLE : View.GONE);

        List<Booking> list = BookingRepository.filter(all, tab);
        int beforeSearch = list.size();
        if (history) list = BookingRepository.search(list, query, historyStatus);

        // Active tab uses its own adapter (bigger card with live timer)
        if (active) {
            if (binding.rvBookings.getAdapter() != activeAdapter) binding.rvBookings.setAdapter(activeAdapter);
            activeAdapter.submitList(list);
            activeAdapter.tick();
        } else {
            if (binding.rvBookings.getAdapter() != cardAdapter) binding.rvBookings.setAdapter(cardAdapter);
            cardAdapter.submitList(list);
        }

        if (!list.isEmpty()) {
            binding.stateView.hide();
            return;
        }
        if (history && beforeSearch > 0) {          // there IS history, the search just finds nothing
            binding.stateView.showEmpty(R.drawable.ic_search, getString(R.string.mb_empty_search),
                    getString(R.string.mb_empty_search_msg), getString(R.string.mb_clear_search), v -> {
                        binding.searchBar.setQuery("");
                        binding.chipsStatus.check(R.id.chipAll);
                    });
            return;
        }
        switch (tab) {
            case BookingRepository.TAB_UPCOMING:
                binding.stateView.showEmpty(R.drawable.ic_bookings, getString(R.string.bookings_empty_upcoming),
                        getString(R.string.bookings_empty_upcoming_msg), getString(R.string.home_find_parking),
                        v -> ((CustomerNavHost) requireActivity()).openExplore(null));
                break;
            case BookingRepository.TAB_ACTIVE:
                binding.stateView.showEmpty(R.drawable.ic_clock, getString(R.string.mb_empty_active),
                        getString(R.string.mb_empty_active_msg), null, null);
                break;
            default:
                binding.stateView.showEmpty(R.drawable.ic_receipt, getString(R.string.mb_empty_history),
                        getString(R.string.mb_empty_history_msg), null, null);
                break;
        }
    }

    /** "Upcoming (2)", "Active (1)", "History" … */
    private void updateTabCounts() {
        for (int i = 0; i < TABS.length; i++) {
            TabLayout.Tab t = binding.tabs.getTabAt(i);
            if (t == null) continue;
            int count = BookingRepository.TAB_HISTORY.equals(TABS[i]) ? 0 : BookingRepository.filter(all, TABS[i]).size();
            String name = getString(TAB_NAMES[i]);
            t.setText(count > 0 ? getString(R.string.bookings_tab_count, name, count) : name);
        }
    }

    // ================================================================== card actions

    @Override
    public void onOpen(@NonNull Booking booking) {
        startActivity(BookingSummaryActivity.intent(requireContext(), booking.getId()));
    }

    @Override
    public void onTicket(@NonNull Booking booking) {
        startActivity(TicketActivity.intent(requireContext(), booking.getId()));
    }

    @Override
    public void onReceipt(@NonNull Booking booking) {
        if (booking.getPaymentId() != null) {
            startActivity(PaymentResultActivity.intent(requireContext(), booking.getPaymentId()));
        }
    }

    @Override
    public void onPay(@NonNull Booking booking) {
        startActivity(PaymentActivity.intent(requireContext(), booking.getId()));
    }

    @Override
    public void onCancel(@NonNull Booking booking) {
        CancelBookingFlow.start(requireContext(), bookingRepository, policy, booking, new CancelBookingFlow.Listener() {
            @Override
            public void onBusy(boolean busy) {
                if (binding == null) return;
                if (busy) binding.loading.show();
                else binding.loading.hide();
            }

            @Override
            public void onMessage(@NonNull String message) {
                if (binding != null) Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            }
        });
    }
}
