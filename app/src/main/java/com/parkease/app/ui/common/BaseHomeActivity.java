package com.parkease.app.ui.common;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.parkease.app.R;
import com.parkease.app.databinding.ActivityHomePlaceholderBinding;
import com.parkease.app.models.User;
import com.parkease.app.repository.AuthRepository;
import com.parkease.app.repository.DataCallback;
import com.parkease.app.repository.UserRepository;
import com.parkease.app.ui.profile.ProfileActivity;
import com.parkease.app.utils.ImageUtils;

/**
 * Shared logic for the two dashboards (customer and admin) until the real ones are built:
 * <ul>
 *   <li>Sends the user to Login if there is no session.</li>
 *   <li>Shows the saved profile instantly (SQLite), then refreshes it from Firebase, so a
 *       user blocked by the admin (or whose role changed) is handled straight away.</li>
 *   <li>Buttons: View profile, Log out.</li>
 * </ul>
 */
public abstract class BaseHomeActivity extends BaseActivity {

    protected ActivityHomePlaceholderBinding binding;
    protected AuthRepository authRepository;
    protected UserRepository userRepository;

    /** The role this screen is for: Constants.ROLE_CUSTOMER or Constants.ROLE_ADMIN. */
    @NonNull
    protected abstract String requiredRole();

    @StringRes
    protected abstract int subtitleRes();

    @StringRes
    protected abstract int comingSoonMessageRes();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        authRepository = new AuthRepository(this);
        userRepository = new UserRepository(this);

        if (!authRepository.isLoggedIn()) {
            AppNavigator.openLogin(this, null);
            return;
        }

        binding = ActivityHomePlaceholderBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyStatusBarPadding(binding.header.getRoot());
        applyStatusBarScrim(binding.statusBarScrim.getRoot());
        applyNavigationBarPadding(binding.scroll);

        binding.header.toolbar.setTitle(R.string.app_name);
        binding.header.tvHeaderSubtitle.setText(subtitleRes());
        binding.comingSoon.setMessage(getString(comingSoonMessageRes()));
        binding.btnProfile.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        binding.btnLogout.setOnClickListener(v -> LogoutHelper.confirmAndLogout(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (binding == null) return;
        User cached = userRepository.getCachedProfile();
        if (cached != null) showProfile(cached);
        refreshProfile();
    }

    private void refreshProfile() {
        userRepository.syncProfile(new DataCallback<User>() {
            @Override
            public void onSuccess(User user) {
                // Wrong dashboard for this role (role changed by admin) → open the right one
                if (!requiredRole().equals(user.getRole())) {
                    AppNavigator.openHome(BaseHomeActivity.this, user);
                    return;
                }
                showProfile(user);
            }

            @Override
            public void onError(@NonNull String message) {
                if (!authRepository.isLoggedIn()) {
                    AppNavigator.openLogin(BaseHomeActivity.this, message);   // blocked / missing
                }
                // otherwise (e.g. offline) keep showing the saved copy quietly
            }
        });
    }

    private void showProfile(@NonNull User user) {
        binding.header.tvHeaderTitle.setText(getString(R.string.home_greeting, user.getFirstName()));
        ImageUtils.showAvatar(binding.imgAvatar, binding.tvAvatar, user);
        binding.tvName.setText(user.getFullName());
        binding.tvEmail.setText(user.getEmail());
        binding.tvPhone.setText(getString(R.string.phone_prefix) + " " + user.getPhone());
        binding.tvRole.setText(user.isAdmin() ? R.string.home_role_admin : R.string.home_role_customer);
    }
}
