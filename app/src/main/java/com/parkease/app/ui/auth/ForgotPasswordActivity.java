package com.parkease.app.ui.auth;

import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;

import com.parkease.app.R;
import com.parkease.app.databinding.ActivityForgotPasswordBinding;
import com.parkease.app.repository.AuthRepository;
import com.parkease.app.repository.DataCallback;
import com.parkease.app.ui.common.BaseActivity;
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.FirebaseHelper;
import com.parkease.app.utils.FormUtils;
import com.parkease.app.utils.Validator;

/**
 * STEP 5 – Forgot password.
 * Firebase emails a secure link; the user chooses a new password on Firebase's page.
 * The app never sees or stores the new password.
 */
public class ForgotPasswordActivity extends BaseActivity {

    private ActivityForgotPasswordBinding binding;
    private AuthRepository authRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authRepository = new AuthRepository(this);

        applyStatusBarPadding(binding.header.getRoot());
        applyStatusBarScrim(binding.statusBarScrim.getRoot());
        applyKeyboardAwarePadding(binding.scroll);

        binding.header.toolbar.setTitle(R.string.app_name);
        binding.header.toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        binding.header.toolbar.setNavigationContentDescription(R.string.action_back);
        binding.header.toolbar.setNavigationOnClickListener(v -> finish());
        binding.header.tvHeaderTitle.setText(R.string.forgot_title);
        binding.header.tvHeaderSubtitle.setText(R.string.forgot_subtitle);

        binding.tvFirebaseWarning.setVisibility(
                FirebaseHelper.isConfigured(this) ? View.GONE : View.VISIBLE);

        // Pre-fill the email typed on the login screen
        String email = getIntent().getStringExtra(Constants.EXTRA_EMAIL);
        if (email != null) binding.etEmail.setText(email);

        FormUtils.clearErrorOnType(binding.tilEmail);
        binding.etEmail.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                attemptSend();
                return true;
            }
            return false;
        });
        binding.btnSend.setOnClickListener(v -> attemptSend());
        binding.successState.setOnActionClickListener(v -> finish());
    }

    private void attemptSend() {
        hideKeyboard();
        String email = Validator.normalizeEmail(FormUtils.text(binding.tilEmail));
        if (!FormUtils.showError(binding.tilEmail, Validator.email(email))) {
            binding.etEmail.requestFocus();
            return;
        }

        setLoading(true);
        authRepository.sendPasswordReset(email, new DataCallback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                setLoading(false);
                showSuccess(email);
            }

            @Override
            public void onError(@NonNull String message) {
                setLoading(false);
                if (message.equals(getString(R.string.err_email_invalid))) {
                    binding.tilEmail.setError(message);
                } else {
                    showMessage(message);
                }
            }
        });
    }

    private void showSuccess(@NonNull String email) {
        binding.successState.setMessage(getString(R.string.forgot_sent_message, email));
        binding.formGroup.setVisibility(View.GONE);
        binding.successState.setAlpha(0f);
        binding.successState.setVisibility(View.VISIBLE);
        binding.successState.animate().alpha(1f).setDuration(250).start();
    }

    private void setLoading(boolean loading) {
        binding.btnSend.setEnabled(!loading);
        if (loading) binding.loading.show(); else binding.loading.hide();
    }
}
