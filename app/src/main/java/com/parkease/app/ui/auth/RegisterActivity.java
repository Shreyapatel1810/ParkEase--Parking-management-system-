package com.parkease.app.ui.auth;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.TextInputLayout;
import com.parkease.app.R;
import com.parkease.app.databinding.ActivityRegisterBinding;
import com.parkease.app.models.User;
import com.parkease.app.repository.AuthRepository;
import com.parkease.app.repository.DataCallback;
import com.parkease.app.ui.common.AppNavigator;
import com.parkease.app.ui.common.BaseActivity;
import com.parkease.app.utils.FirebaseHelper;
import com.parkease.app.utils.FormUtils;
import com.parkease.app.utils.SessionManager;
import com.parkease.app.utils.Validator;

/**
 * STEP 5 – Registration.
 * Validates every field, creates the Firebase Authentication account, saves the profile
 * (role = customer) to the Realtime Database, then opens the customer dashboard.
 */
public class RegisterActivity extends BaseActivity {

    private ActivityRegisterBinding binding;
    private AuthRepository authRepository;
    private TextInputLayout[] fields;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authRepository = new AuthRepository(this);
        fields = new TextInputLayout[]{binding.tilName, binding.tilEmail, binding.tilPhone,
                binding.tilPassword, binding.tilConfirm};

        applyStatusBarPadding(binding.header.getRoot());
        applyStatusBarScrim(binding.statusBarScrim.getRoot());
        applyKeyboardAwarePadding(binding.scroll);

        binding.header.toolbar.setTitle(R.string.app_name);
        binding.header.toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        binding.header.toolbar.setNavigationContentDescription(R.string.action_back);
        binding.header.toolbar.setNavigationOnClickListener(v -> finish());
        binding.header.tvHeaderTitle.setText(R.string.register_title);
        binding.header.tvHeaderSubtitle.setText(R.string.register_subtitle);

        binding.tvFirebaseWarning.setVisibility(
                FirebaseHelper.isConfigured(this) ? View.GONE : View.VISIBLE);

        FormUtils.clearErrorOnType(fields);
        setupPasswordStrengthMeter();

        binding.etConfirm.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptRegister();
                return true;
            }
            return false;
        });
        binding.btnRegister.setOnClickListener(v -> attemptRegister());
        binding.btnGoLogin.setOnClickListener(v -> finish()); // Login is the screen behind
    }

    // ------------------------------------------------------------------ submit

    private void attemptRegister() {
        hideKeyboard();
        String name = Validator.normalizeName(FormUtils.text(binding.tilName));
        String email = Validator.normalizeEmail(FormUtils.text(binding.tilEmail));
        String phone = Validator.normalizePhone(FormUtils.text(binding.tilPhone));
        String password = FormUtils.text(binding.tilPassword);
        String confirm = FormUtils.text(binding.tilConfirm);

        // Check every field (so the user sees all problems at once)
        boolean ok = FormUtils.showError(binding.tilName, Validator.fullName(name));
        ok &= FormUtils.showError(binding.tilEmail, Validator.email(email));
        ok &= FormUtils.showError(binding.tilPhone, Validator.phone(phone));
        ok &= FormUtils.showError(binding.tilPassword, Validator.strongPassword(password));
        ok &= FormUtils.showError(binding.tilConfirm, Validator.confirmPassword(password, confirm));
        if (!ok) {
            FormUtils.focusFirstError(fields);
            return;
        }

        setLoading(true);
        authRepository.register(name, email, phone, password, new DataCallback<User>() {
            @Override
            public void onSuccess(User user) {
                SessionManager.setRememberMe(RegisterActivity.this, true);
                Toast.makeText(RegisterActivity.this,
                        getString(R.string.register_success, user.getFirstName()),
                        Toast.LENGTH_LONG).show();
                AppNavigator.openHome(RegisterActivity.this, user);
            }

            @Override
            public void onError(@NonNull String message) {
                setLoading(false);
                // Duplicate email → show it right under the email field
                if (message.equals(getString(R.string.err_email_exists))) {
                    binding.tilEmail.setError(message);
                    binding.etEmail.requestFocus();
                } else {
                    showMessage(message);
                }
            }
        });
    }

    // ------------------------------------------------------------------ strength meter

    private void setupPasswordStrengthMeter() {
        binding.etPassword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void afterTextChanged(Editable s) {
                updateStrength(Validator.passwordStrength(s.toString()));
            }
        });
    }

    private void updateStrength(int score) {
        binding.strengthGroup.setVisibility(score == 0 ? View.GONE : View.VISIBLE);
        if (score == 0) return;

        @ColorRes int color;
        @StringRes int label;
        switch (score) {
            case 1:
                color = R.color.slot_occupied;
                label = R.string.strength_weak;
                break;
            case 2:
                color = R.color.slot_reserved;
                label = R.string.strength_fair;
                break;
            case 3:
                color = R.color.brand_secondary;
                label = R.string.strength_good;
                break;
            default:
                color = R.color.slot_available;
                label = R.string.strength_strong;
                break;
        }
        int c = ContextCompat.getColor(this, color);
        binding.strengthBar.setProgressCompat(score, true);
        binding.strengthBar.setIndicatorColor(c);
        binding.strengthBar.setTrackColor(ContextCompat.getColor(this, R.color.bg_subtle));
        binding.tvStrength.setText(label);
        binding.tvStrength.setTextColor(ColorStateList.valueOf(c));
    }

    private void setLoading(boolean loading) {
        binding.btnRegister.setEnabled(!loading);
        if (loading) binding.loading.show(); else binding.loading.hide();
    }
}
