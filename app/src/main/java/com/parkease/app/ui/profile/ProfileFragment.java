package com.parkease.app.ui.profile;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.parkease.app.R;
import com.parkease.app.databinding.FragmentProfileBinding;
import com.parkease.app.databinding.ItemMenuRowBinding;
import com.parkease.app.databinding.ItemProfileRowBinding;
import com.parkease.app.models.User;
import com.parkease.app.repository.AuthRepository;
import com.parkease.app.repository.DataCallback;
import com.parkease.app.repository.UserRepository;
import com.parkease.app.ui.common.AppNavigator;
import com.parkease.app.ui.common.LogoutHelper;
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.ImageUtils;
import com.parkease.app.utils.InsetsHelper;
import com.parkease.app.utils.NetworkUtils;

import java.text.DateFormat;
import java.util.Date;

/**
 * STEP 6/7 – User profile. Used as the customer "Profile" tab and inside ProfileActivity
 * (admins). Pass {@link Constants#EXTRA_SHOW_BACK} = true to show a back arrow.
 *
 * Loading:  1) SQLite copy instantly  2) sync from Firebase  3) show fresh data.
 * Actions:  change/remove photo, edit profile, change password, log out.
 */
public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private UserRepository userRepository;
    private User currentUser;

    private final ActivityResultLauncher<PickVisualMediaRequest> photoPicker =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), this::onPhotoPicked);

    @NonNull
    public static ProfileFragment newInstance(boolean showBack) {
        ProfileFragment f = new ProfileFragment();
        Bundle args = new Bundle();
        args.putBoolean(Constants.EXTRA_SHOW_BACK, showBack);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        userRepository = new UserRepository(requireContext());
        boolean standalone = getArguments() != null && getArguments().getBoolean(Constants.EXTRA_SHOW_BACK);

        InsetsHelper.statusBarPadding(binding.header.getRoot());
        InsetsHelper.statusBarScrim(binding.statusBarScrim.getRoot());
        if (standalone) InsetsHelper.navigationBarPadding(binding.scroll);   // no bottom nav below

        binding.header.toolbar.setTitle(R.string.app_name);
        if (standalone) {
            binding.header.toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
            binding.header.toolbar.setNavigationContentDescription(R.string.action_back);
            binding.header.toolbar.setNavigationOnClickListener(v -> requireActivity().finish());
        }
        binding.header.tvHeaderTitle.setText(R.string.profile_title);
        binding.header.tvHeaderSubtitle.setText(R.string.profile_subtitle);

        setupMenuRow(binding.menuEdit, R.drawable.ic_edit, R.string.profile_edit, R.string.profile_edit_sub,
                v -> startActivity(new Intent(requireContext(), EditProfileActivity.class)));
        setupMenuRow(binding.menuPassword, R.drawable.ic_key, R.string.profile_change_password,
                R.string.profile_change_password_sub,
                v -> startActivity(new Intent(requireContext(), ChangePasswordActivity.class)));

        binding.avatarContainer.setOnClickListener(v -> showPhotoOptions());
        binding.btnLogout.setOnClickListener(v -> LogoutHelper.confirmAndLogout(requireActivity()));
    }

    @Override
    public void onResume() {
        super.onResume();
        User cached = userRepository.getCachedProfile();
        if (cached != null) showUser(cached);
        syncFromFirebase();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void syncFromFirebase() {
        if (!NetworkUtils.isOnline(requireContext())) {
            showNotice(getString(R.string.profile_offline));
            return;
        }
        userRepository.syncProfile(new DataCallback<User>() {
            @Override
            public void onSuccess(User user) {
                if (binding == null) return;
                binding.tvSyncNotice.setVisibility(View.GONE);
                showUser(user);
            }

            @Override
            public void onError(@NonNull String message) {
                if (binding == null) return;
                if (!new AuthRepository(requireContext()).isLoggedIn()) {
                    AppNavigator.openLogin(requireActivity(), message);
                } else {
                    showNotice(getString(R.string.profile_sync_failed));
                }
            }
        });
    }

    // ------------------------------------------------------------------ display

    private void showUser(@NonNull User user) {
        if (binding == null) return;
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

    private void message(@NonNull String text) {
        if (binding != null) Snackbar.make(binding.getRoot(), text, Snackbar.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------------ photo

    private void showPhotoOptions() {
        boolean hasPhoto = currentUser != null && currentUser.hasPhoto();
        String[] options = hasPhoto
                ? new String[]{getString(R.string.photo_choose), getString(R.string.photo_remove)}
                : new String[]{getString(R.string.photo_choose)};

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.photo_title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        photoPicker.launch(new PickVisualMediaRequest.Builder()
                                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                                .build());
                    } else {
                        binding.loading.show();
                        userRepository.removePhoto(photoCallback(R.string.photo_removed));
                    }
                })
                .show();
    }

    private void onPhotoPicked(@Nullable Uri uri) {
        if (uri == null || binding == null) return;
        binding.loading.show();
        ImageUtils.encodeProfilePhoto(requireContext(), uri, base64 -> {
            if (binding == null) return;
            if (base64 == null) {
                binding.loading.hide();
                message(getString(R.string.photo_read_failed));
                return;
            }
            userRepository.updatePhoto(base64, photoCallback(R.string.photo_updated));
        });
    }

    @NonNull
    private DataCallback<User> photoCallback(@StringRes int successMessage) {
        return new DataCallback<User>() {
            @Override
            public void onSuccess(User user) {
                if (binding == null) return;
                binding.loading.hide();
                if (user != null) showUser(user);
                message(getString(successMessage));
            }

            @Override
            public void onError(@NonNull String error) {
                if (binding == null) return;
                binding.loading.hide();
                message(error);
            }
        };
    }
}
