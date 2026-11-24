package com.parkease.app.ui.customer;

import androidx.annotation.NonNull;

import com.parkease.app.R;
import com.parkease.app.ui.common.BaseHomeActivity;
import com.parkease.app.utils.Constants;

/**
 * Customer dashboard (placeholder for Step 5).
 * The real version – bottom navigation with Home, Bookings, Favourites and Profile –
 * replaces this in the customer module steps.
 */
public class CustomerMainActivity extends BaseHomeActivity {

    @NonNull
    @Override
    protected String requiredRole() {
        return Constants.ROLE_CUSTOMER;
    }

    @Override
    protected int subtitleRes() {
        return R.string.home_customer_subtitle;
    }

    @Override
    protected int comingSoonMessageRes() {
        return R.string.home_customer_coming_msg;
    }
}
