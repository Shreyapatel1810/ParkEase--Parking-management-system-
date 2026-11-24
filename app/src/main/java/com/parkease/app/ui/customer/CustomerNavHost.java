package com.parkease.app.ui.customer;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * What the customer tabs can ask the main screen to do.
 * CustomerMainActivity implements it; fragments call it with
 * {@code ((CustomerNavHost) requireActivity()).openTab(R.id.nav_bookings)}.
 */
public interface CustomerNavHost {

    /** Switches the bottom navigation to a tab (R.id.nav_home, nav_explore, …). */
    void openTab(@IdRes int tabId);

    /** Opens Explore, optionally with search text already filled in. */
    void openExplore(@Nullable String query);

    /** Opens the details screen of one parking location. */
    void openLocation(@NonNull String locationId);

    /** Opens the notification inbox. */
    void openNotifications();
}
