package com.parkease.app.ui.profile;

import android.os.Bundle;

import com.parkease.app.R;
import com.parkease.app.repository.AuthRepository;
import com.parkease.app.ui.common.AppNavigator;
import com.parkease.app.ui.common.BaseActivity;

/**
 * Profile as a separate screen (used by the admin dashboard).
 * All the profile logic lives in {@link ProfileFragment}, which customers also see as a tab.
 */
public class ProfileActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!new AuthRepository(this).isLoggedIn()) {
            AppNavigator.openLogin(this, null);
            return;
        }
        setContentView(R.layout.activity_fragment_host);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, ProfileFragment.newInstance(true))
                    .commit();
        }
    }
}
