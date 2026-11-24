package com.parkease.app.ui.common;

import android.app.Activity;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.parkease.app.R;
import com.parkease.app.repository.AuthRepository;

/** "Log out?" confirmation used by every screen that has a Log out button. */
public final class LogoutHelper {

    private LogoutHelper() { }

    public static void confirmAndLogout(@NonNull Activity activity) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.logout_confirm_title)
                .setMessage(R.string.logout_confirm_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_logout, (dialog, which) -> {
                    // Firebase sign-out + delete cached profile, tickets etc. from this phone
                    new AuthRepository(activity).logout();
                    AppNavigator.openLogin(activity, activity.getString(R.string.logged_out));
                })
                .show();
    }
}
