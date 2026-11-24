package com.parkease.app.ui.customer;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.parkease.app.R;
import com.parkease.app.databinding.ActivityCustomerMainBinding;
import com.parkease.app.models.User;
import com.parkease.app.repository.AuthRepository;
import com.parkease.app.repository.DataCallback;
import com.parkease.app.repository.UserRepository;
import com.parkease.app.ui.common.AppNavigator;
import com.parkease.app.ui.common.BaseActivity;
import com.parkease.app.ui.customer.booking.MyBookingsFragment;
import com.parkease.app.ui.customer.favourite.FavouritesFragment;
import com.parkease.app.ui.customer.home.HomeFragment;
import com.parkease.app.ui.customer.location.LocationDetailsActivity;
import com.parkease.app.ui.customer.notification.NotificationsActivity;
import com.parkease.app.ui.customer.search.ExploreFragment;
import com.parkease.app.ui.profile.ProfileFragment;
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.NetworkMonitor;

/**
 * STEP 7 – Customer main screen with bottom navigation:
 * Home · Explore · My Bookings · Favourites · Profile.
 *
 * Each tab is a Fragment. They are created once and then shown/hidden (not recreated),
 * so switching tabs is instant and each tab keeps its scroll position and search text.
 * Back on any tab returns to Home; Back on Home leaves the app.
 */
public class CustomerMainActivity extends BaseActivity implements CustomerNavHost {

    private static final String KEY_TAB = "selected_tab";

    private ActivityCustomerMainBinding binding;
    private NetworkMonitor networkMonitor;
    private UserRepository userRepository;
    @IdRes private int currentTab = R.id.nav_home;

    private final OnBackPressedCallback backToHome = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            binding.bottomNav.setSelectedItemId(R.id.nav_home);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!new AuthRepository(this).isLoggedIn()) {
            AppNavigator.openLogin(this, null);
            return;
        }
        binding = ActivityCustomerMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        userRepository = new UserRepository(this);

        if (savedInstanceState != null) currentTab = savedInstanceState.getInt(KEY_TAB, R.id.nav_home);

        binding.bottomNav.setOnItemSelectedListener(item -> {
            showTab(item.getItemId());
            return true;
        });
        binding.bottomNav.setOnItemReselectedListener(item -> { /* already there */ });
        getOnBackPressedDispatcher().addCallback(this, backToHome);

        binding.bottomNav.setSelectedItemId(currentTab);
        showTab(currentTab);

        networkMonitor = new NetworkMonitor(this, online ->
                binding.tvOffline.setVisibility(online ? View.GONE : View.VISIBLE));
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (networkMonitor != null) networkMonitor.start();
    }

    @Override
    protected void onStop() {
        if (networkMonitor != null) networkMonitor.stop();
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (binding != null) checkAccount();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_TAB, currentTab);
    }

    // ================================================================== tabs

    private void showTab(@IdRes int tabId) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction tx = fm.beginTransaction().setReorderingAllowed(true);

        // Hide every tab that is currently added
        for (int id : new int[]{R.id.nav_home, R.id.nav_explore, R.id.nav_bookings,
                R.id.nav_favourites, R.id.nav_profile}) {
            Fragment f = fm.findFragmentByTag(tag(id));
            if (f != null && id != tabId) tx.hide(f);
        }
        // Show (or create on first use) the chosen tab
        Fragment target = fm.findFragmentByTag(tag(tabId));
        if (target == null) {
            tx.add(R.id.fragmentContainer, createTab(tabId), tag(tabId));
        } else {
            tx.show(target);
        }
        tx.commitNow();

        currentTab = tabId;
        backToHome.setEnabled(tabId != R.id.nav_home);
    }

    @NonNull
    private Fragment createTab(@IdRes int tabId) {
        if (tabId == R.id.nav_explore) return new ExploreFragment();
        if (tabId == R.id.nav_bookings) return new MyBookingsFragment();
        if (tabId == R.id.nav_favourites) return new FavouritesFragment();
        if (tabId == R.id.nav_profile) return ProfileFragment.newInstance(false);
        return new HomeFragment();
    }

    @NonNull
    private static String tag(@IdRes int tabId) {
        return "tab_" + tabId;
    }

    // ================================================================== CustomerNavHost

    @Override
    public void openTab(@IdRes int tabId) {
        binding.bottomNav.setSelectedItemId(tabId);
    }

    @Override
    public void openExplore(@Nullable String query) {
        binding.bottomNav.setSelectedItemId(R.id.nav_explore);
        Fragment f = getSupportFragmentManager().findFragmentByTag(tag(R.id.nav_explore));
        if (f instanceof ExploreFragment) ((ExploreFragment) f).setQuery(query);
    }

    @Override
    public void openLocation(@NonNull String locationId) {
        Intent intent = new Intent(this, LocationDetailsActivity.class);
        intent.putExtra(Constants.EXTRA_LOCATION_ID, locationId);
        startActivity(intent);
    }

    @Override
    public void openNotifications() {
        startActivity(new Intent(this, NotificationsActivity.class));
    }

    // ================================================================== account checks

    /** Blocked by the admin, or made an admin? React straight away. */
    private void checkAccount() {
        userRepository.syncProfile(new DataCallback<User>() {
            @Override
            public void onSuccess(User user) {
                if (user.isAdmin()) AppNavigator.openHome(CustomerMainActivity.this, user);
            }

            @Override
            public void onError(@NonNull String message) {
                if (!new AuthRepository(CustomerMainActivity.this).isLoggedIn()) {
                    AppNavigator.openLogin(CustomerMainActivity.this, message);
                }
                // offline etc.: keep working with saved data
            }
        });
    }
}
