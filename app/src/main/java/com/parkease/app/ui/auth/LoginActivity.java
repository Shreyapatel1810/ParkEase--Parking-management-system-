package com.parkease.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;

import com.parkease.app.R;
import com.parkease.app.databinding.ActivityLoginBinding;
import com.parkease.app.models.User;
import com.parkease.app.repository.AuthRepository;
import com.parkease.app.repository.DataCallback;
import com.parkease.app.ui.common.AppNavigator;
import com.parkease.app.ui.common.BaseActivity;
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.FirebaseHelper;
import com.parkease.app.utils.FormUtils;
import com.parkease.app.utils.SessionManager;
import com.parkease.app.utils.Validator;

/**
 * STEP 5 – Login (for both customers and admins).
 * After a successful Firebase login the profile is loaded and the user goes to the
 * dashboard that matches their role. There is no "fake" login: only Firebase decides.
 */
public class LoginActivity extends BaseActivity {

    private ActivityLoginBinding binding;
    private AuthRepository authRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authRepository = new AuthRepository(this);

        applyStatusBarPadding(binding.header.getRoot());
        applyStatusBarScrim(binding.statusBarScrim.getRoot());
        applyKeyboardAwarePadding(binding.scroll);

        binding.header.toolbar.setTitle(R.string.app_name);
        binding.header.tvHeaderTitle.setText(R.string.login_title);
        binding.header.tvHeaderSubtitle.setText(R.string.login_subtitle);

        binding.tvFirebaseWarning.setVisibility(
                FirebaseHelper.isConfigured(this) ? View.GONE : View.VISIBLE);

        FormUtils.clearErrorOnType(binding.tilEmail, binding.tilPassword);

        binding.etPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin();
                return true;
            }
            return false;
        });
        binding.btnLogin.setOnClickListener(v -> attemptLogin());
        binding.btnForgot.setOnClickListener(v -> openForgotPassword());
        binding.btnGoRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));

        // Message passed from another screen, e.g. "Your account has been blocked"
        String message = getIntent().getStringExtra(Constants.EXTRA_MESSAGE);
        if (message != null && savedInstanceState == null) {
            showMessage(message);
        }
    }

    private void attemptLogin() {
        hideKeyboard();
        String email = Validator.normalizeEmail(FormUtils.text(binding.tilEmail));
        String password = FormUtils.text(binding.tilPassword);

        // At login we only check that fields are filled and the email looks right.
        // Password strength is checked at registration, not here.
        boolean ok = FormUtils.showError(binding.tilEmail, Validator.email(email));
        ok &= FormUtils.showError(binding.tilPassword,
                password.isEmpty() ? R.string.err_required : 0);
        if (!ok) {
            FormUtils.focusFirstError(binding.tilEmail, binding.tilPassword);
            return;
        }

        setLoading(true);
        authRepository.login(email, password, new DataCallback<User>() {
            @Override
            public void onSuccess(User user) {
                SessionManager.setRememberMe(LoginActivity.this, binding.cbRemember.isChecked());
                AppNavigator.openHome(LoginActivity.this, user);
            }

            @Override
            public void onError(@NonNull String message) {
                setLoading(false);
                if (message.equals(getString(R.string.err_invalid_credentials))) {
                    binding.etPassword.setText("");          // clear first (this removes old errors)
                    binding.tilPassword.setError(message);   // then show the new one
                    binding.etPassword.requestFocus();
                } else {
                    showMessage(message);
                }
            }
        });
    }

    private void openForgotPassword() {
        Intent intent = new Intent(this, ForgotPasswordActivity.class);
        intent.putExtra(Constants.EXTRA_EMAIL, FormUtils.text(binding.tilEmail).trim());
        startActivity(intent);
    }

    private void setLoading(boolean loading) {
        binding.btnLogin.setEnabled(!loading);
        if (loading) binding.loading.show(); else binding.loading.hide();
    }
}
