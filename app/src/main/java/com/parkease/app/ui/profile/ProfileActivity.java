package com.parkease.app.ui.profile;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.parkease.app.R;
import com.parkease.app.databinding.ActivityProfileBinding;
import com.parkease.app.databinding.ItemMenuRowBinding;
import com.parkease.app.databinding.ItemProfileRowBinding;
import com.parkease.app.models.User;
import com.parkease.app.repository.AuthRepository;
import com.parkease.app.repository.DataCallback;
import com.parkease.app.repository.UserRepository;
import com.parkease.app.ui.common.AppNavigator;
import com.parkease.app.ui.common.BaseActivity;
import com.parkease.app.ui.common.LogoutHelper;
import com.parkease.app.utils.ImageUtils;
import com.parkease.app.utils.NetworkUtils;

import java.text.DateFormat;
import java.util.Date;

/**
 * STEP 6 – User profile (customers and admins).
 *
 * Loading:  1) show the SQLite copy instantly  2) sync from Firebase  3) show fresh data.
 * Offline:  the saved copy stays visible with a notice.
 * Actions:  change/remove photo, edit profile, change password, log out.
 */
public class ProfileActivity extends BaseActivity {

    private ActivityProfileBinding binding;
    private UserRepository userRepository;
    private User currentUser;

    /** Android photo picker: lets the user pick one image, no storage permission needed. */
    private final ActivityResultLauncher<PickVisualMediaRequest> photoPicker =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), this::onPhotoPicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!new AuthRepository(this).isLoggedIn()) {
            AppNavigator.openLogin(this, null);
            return;
        }
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        userRepository = new UserRepository(this);

        applyStatusBarPadding(binding.header.getRoot());
        applyStatusBarScrim(binding.statusBarScrim.getRoot());
        applyNavigationBarPadding(binding.scroll);

        binding.header.toolbar.setTitle(R.string.app_name);
        binding.header.toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        binding.header.toolbar.setNavigationContentDescription(R.string.action_back);
        binding.header.toolbar.setNavigationOnClickListener(v -> finish());
        binding.header.tvHeaderTitle.setText(R.string.profile_title);
        binding.header.tvHeaderSubtitle.setText(R.string.profile_subtitle);

        setupMenuRow(binding.menuEdit, R.drawable.ic_edit, R.string.profile_edit, R.string.profile_edit_sub,
                v -> startActivity(new Intent(this, EditProfileActivity.class)));
        setupMenuRow(binding.menuPassword, R.drawable.ic_key, R.string.profile_change_password,
                R.string.profile_change_password_sub,
                v -> startActivity(new Intent(this, ChangePasswordActivity.class)));

        binding.avatarContainer.setOnClickListener(v -> showPhotoOptions());
        binding.btnLogout.setOnClickListener(v -> LogoutHelper.confirmAndLogout(this));
    }

    /** Runs every time the screen becomes visible – also after returning from Edit profile. */
    @Override
    protected void onResume() {
        super.onResume();
        if (binding == null) return;
        // 1) instant: the copy saved on the phone
        User cached = userRepository.getCachedProfile();
        if (cached != null) showUser(cached);
        // 2) then refresh from Firebase
        syncFromFirebase();
    }

    private void syncFromFirebase() {
        if (!NetworkUtils.isOnline(this)) {
            showNotice(getString(R.string.profile_offline));
            return;
        }
        userRepository.syncProfile(new DataCallback<User>() {
            @Override
            public void onSuccess(User user) {
                binding.tvSyncNotice.setVisibility(View.GONE);
                showUser(user);
            }

            @Override
            public void onError(@NonNull String message) {
                if (!new AuthRepository(ProfileActivity.this).isLoggedIn()) {
                    AppNavigator.openLogin(ProfileActivity.this, message);   // blocked / removed
                } else {
                    showNotice(getString(R.string.profile_sync_failed));
                }
            }
        });
    }

    // ------------------------------------------------------------------ display

    private void showUser(@NonNull User user) {
        currentUser = user;
        ImageUtils.showAvatar(binding.imgPhoto, binding.tvInitials, user);
        binding.tvName.setText(user.getFullName());
        binding.tvRole.setText(user.isAdmin() ? R.string.home_role_admin : R.string.home_role_customer);

        setRow(binding.rowName, R.drawable.ic_profile, R.string.field_full_name, user.getFullName());
        setRow(binding.rowEmail, R.drawable.ic_email, R.string.field_email, user.getEmail());
        setRow(binding.rowPhone, R.drawable.ic_phone, R.string.field_phone,
                getString(R.string.phone_prefix) + " " + user.getPhone());
        setRow(binding.rowMemberSince, R.drawable.ic_clock, R.string.profile_member_since,
                user.getCreatedAt() > 0
                        ? DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(user.getCreatedAt()))
                        : "—");

        long synced = userRepository.getLastSyncedAt();
        binding.tvLastSynced.setText(synced > 0
                ? getString(R.string.profile_last_synced, DateUtils.getRelativeTimeSpanString(synced))
                : "");
    }

    private void setRow(@NonNull ItemProfileRowBinding row, @DrawableRes int icon,
                        @StringRes int label, @Nullable String value) {
        row.imgIcon.setImageResource(icon);
        row.tvLabel.setText(label);
        row.tvValue.setText(value == null || value.isEmpty() ? "—" : value);
    }

    private void setupMenuRow(@NonNull ItemMenuRowBinding row, @DrawableRes int icon,
                              @StringRes int title, @StringRes int subtitle,
                              @NonNull View.OnClickListener onClick) {
        row.imgIcon.setImageResource(icon);
        row.tvTitle.setText(title);
        row.tvSubtitle.setText(subtitle);
        row.getRoot().setOnClickListener(onClick);
    }

    private void showNotice(@NonNull String text) {
        binding.tvSyncNotice.setText(text);
        binding.tvSyncNotice.setVisibility(View.VISIBLE);
    }

    // ------------------------------------------------------------------ photo

    private void showPhotoOptions() {
        boolean hasPhoto = currentUser != null && currentUser.hasPhoto();
        String[] options = hasPhoto
                ? new String[]{getString(R.string.photo_choose), getString(R.string.photo_remove)}
                : new String[]{getString(R.string.photo_choose)};

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.photo_title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        photoPicker.launch(new PickVisualMediaRequest.Builder()
                                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                                .build());
                    } else {
                        removePhoto();
                    }
                })
                .show();
    }

    private void onPhotoPicked(@Nullable Uri uri) {
        if (uri == null) return;             // user closed the picker
        binding.loading.show();
        ImageUtils.encodeProfilePhoto(this, uri, base64 -> {
            if (base64 == null) {
                binding.loading.hide();
                showMessage(R.string.photo_read_failed);
                return;
            }
            userRepository.updatePhoto(base64, photoCallback(R.string.photo_updated));
        });
    }

    private void removePhoto() {
        binding.loading.show();
        userRepository.removePhoto(photoCallback(R.string.photo_removed));
    }

    @NonNull
    private DataCallback<User> photoCallback(@StringRes int successMessage) {
        return new DataCallback<User>() {
            @Override
            public void onSuccess(User user) {
                binding.loading.hide();
                if (user != null) showUser(user);
                showMessage(successMessage);
            }

            @Override
            public void onError(@NonNull String message) {
                binding.loading.hide();
                showMessage(message);
            }
        };
    }
}
