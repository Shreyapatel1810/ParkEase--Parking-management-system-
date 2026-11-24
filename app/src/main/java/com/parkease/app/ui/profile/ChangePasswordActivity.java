package com.parkease.app.ui.profile;

import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.parkease.app.R;
import com.parkease.app.databinding.ActivityChangePasswordBinding;
import com.parkease.app.repository.DataCallback;
import com.parkease.app.repository.UserRepository;
import com.parkease.app.ui.common.BaseActivity;
import com.parkease.app.utils.FormUtils;
import com.parkease.app.utils.Validator;

/**
 * STEP 6 – Change password.
 * Current password (to prove it's really the owner) → new strong password → confirm.
 * Firebase Authentication does the change; the app never saves any password.
 */
public class ChangePasswordActivity extends BaseActivity {

    private ActivityChangePasswordBinding binding;
    private UserRepository userRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChangePasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        userRepository = new UserRepository(this);

        applyStatusBarPadding(binding.header.getRoot());
        applyStatusBarScrim(binding.statusBarScrim.getRoot());
        applyKeyboardAwarePadding(binding.scroll);

        binding.header.toolbar.setTitle(R.string.app_name);
        binding.header.toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        binding.header.toolbar.setNavigationContentDescription(R.string.action_back);
        binding.header.toolbar.setNavigationOnClickListener(v -> finish());
        binding.header.tvHeaderTitle.setText(R.string.change_password_title);
        binding.header.tvHeaderSubtitle.setText(R.string.change_password_subtitle);

        FormUtils.clearErrorOnType(binding.tilCurrent, binding.tilNew, binding.tilConfirm);
        binding.etConfirm.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });
        binding.btnUpdate.setOnClickListener(v -> submit());
    }

    private void submit() {
        hideKeyboard();
        String current = FormUtils.text(binding.tilCurrent);
        String newPassword = FormUtils.text(binding.tilNew);
        String confirm = FormUtils.text(binding.tilConfirm);

        boolean ok = FormUtils.showError(binding.tilCurrent, current.isEmpty() ? R.string.err_required : 0);
        int newError = Validator.strongPassword(newPassword);
        if (newError == 0 && newPassword.equals(current)) newError = R.string.err_password_same;
        ok &= FormUtils.showError(binding.tilNew, newError);
        ok &= FormUtils.showError(binding.tilConfirm, Validator.confirmPassword(newPassword, confirm));
        if (!ok) {
            FormUtils.focusFirstError(binding.tilCurrent, binding.tilNew, binding.tilConfirm);
            return;
        }

        setLoading(true);
        userRepository.changePassword(current, newPassword, new DataCallback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                Toast.makeText(ChangePasswordActivity.this, R.string.change_password_done, Toast.LENGTH_LONG).show();
                finish();
            }

            @Override
            public void onError(@NonNull String message) {
                setLoading(false);
                if (message.equals(getString(R.string.err_current_password_wrong))) {
                    binding.etCurrent.setText("");
                    binding.tilCurrent.setError(message);
                    binding.etCurrent.requestFocus();
                } else {
                    showMessage(message);
                }
            }
        });
    }

    private void setLoading(boolean loading) {
        binding.btnUpdate.setEnabled(!loading);
        if (loading) binding.loading.show(); else binding.loading.hide();
    }
}
