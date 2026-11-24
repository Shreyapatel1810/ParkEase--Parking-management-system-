package com.parkease.app.ui.admin;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.parkease.app.R;
import com.parkease.app.repository.DataCallback;
import com.parkease.app.repository.SampleDataSeeder;
import com.parkease.app.ui.common.BaseHomeActivity;
import com.parkease.app.utils.Constants;

/**
 * Admin dashboard (placeholder until the admin module).
 * Only users whose role is "admin" reach this screen; if a customer somehow opens it,
 * BaseHomeActivity sends them to the customer dashboard instead.
 *
 * Step 7 adds "Add sample parking data" so the customer screens can be tested with real
 * Firebase data before the "Manage locations" screens exist.
 */
public class AdminMainActivity extends BaseHomeActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (binding == null) return;      // not logged in → already sent to Login
        binding.btnSeed.setVisibility(View.VISIBLE);
        binding.btnSeed.setOnClickListener(v -> confirmSeed());
    }

    private void confirmSeed() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.admin_seed_title)
                .setMessage(R.string.admin_seed_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.admin_seed_confirm, (d, w) -> seed())
                .show();
    }

    private void seed() {
        binding.btnSeed.setEnabled(false);
        binding.btnSeed.setText(R.string.loading_seed);
        new SampleDataSeeder(this).seed(new DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                binding.btnSeed.setEnabled(true);
                binding.btnSeed.setText(R.string.admin_seed_button);
                new MaterialAlertDialogBuilder(AdminMainActivity.this)
                        .setMessage(R.string.admin_seed_done)
                        .setPositiveButton(R.string.action_ok, null)
                        .show();
            }

            @Override
            public void onError(@NonNull String message) {
                binding.btnSeed.setEnabled(true);
                binding.btnSeed.setText(R.string.admin_seed_button);
                showMessage(message);
            }
        });
    }

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
