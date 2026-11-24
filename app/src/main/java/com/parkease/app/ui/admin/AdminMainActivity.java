package com.parkease.app.ui.admin;

import androidx.annotation.NonNull;

import com.parkease.app.R;
import com.parkease.app.ui.common.BaseHomeActivity;
import com.parkease.app.utils.Constants;

/**
 * Admin dashboard (placeholder for Step 5).
 * Only users whose role is "admin" in the database reach this screen; if a customer
 * somehow opens it, BaseHomeActivity sends them to the customer dashboard instead.
 * The real admin dashboard (stats, charts, management) replaces this in the admin steps.
 */
public class AdminMainActivity extends BaseHomeActivity {

    @NonNull
    @Override
    protected String requiredRole() {
        return Constants.ROLE_ADMIN;
    }

    @Override
    protected int subtitleRes() {
        return R.string.home_admin_subtitle;
    }

    @Override
    protected int comingSoonMessageRes() {
        return R.string.home_admin_coming_msg;
    }
}
