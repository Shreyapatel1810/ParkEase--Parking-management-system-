package com.parkease.app.ui.profile;

import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.parkease.app.R;
import com.parkease.app.databinding.ActivityEditProfileBinding;
import com.parkease.app.models.User;
import com.parkease.app.repository.DataCallback;
import com.parkease.app.repository.UserRepository;
import com.parkease.app.ui.common.BaseActivity;
import com.parkease.app.utils.FormUtils;
import com.parkease.app.utils.Validator;

/**
 * STEP 6 – Edit profile: full name and mobile number.
 * Saves to Firebase first; the SQLite copy is updated only after Firebase accepts it.
 */
public class EditProfileActivity extends BaseActivity {

    private ActivityEditProfileBinding binding;
    private UserRepository userRepository;
    private User original;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        userRepository = new UserRepository(this);

        applyStatusBarPadding(binding.header.getRoot());
        applyStatusBarScrim(binding.statusBarScrim.getRoot());
        applyKeyboardAwarePadding(binding.scroll);

        binding.header.toolbar.setTitle(R.string.app_name);
        binding.header.toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        binding.header.toolbar.setNavigationContentDescription(R.string.action_back);
        binding.header.toolbar.setNavigationOnClickListener(v -> finish());
        binding.header.tvHeaderTitle.setText(R.string.edit_profile_title);
        binding.header.tvHeaderSubtitle.setText(R.string.edit_profile_subtitle);

        // Fill the form from the saved copy
        original = userRepository.getCachedProfile();
        if (original == null) {
            finish();          // nothing to edit (e.g. logged out meanwhile)
            return;
        }
        if (savedInstanceState == null) {
            binding.etName.setText(original.getFullName());
            binding.etPhone.setText(original.getPhone());
        }
        binding.etEmail.setText(original.getEmail());

        FormUtils.clearErrorOnType(binding.tilName, binding.tilPhone);
        binding.etPhone.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                save();
                return true;
            }
            return false;
        });
        binding.btnSave.setOnClickListener(v -> save());
    }

    private void save() {
        hideKeyboard();
        String name = Validator.normalizeName(FormUtils.text(binding.tilName));
        String phone = Validator.normalizePhone(FormUtils.text(binding.tilPhone));

        boolean ok = FormUtils.showError(binding.tilName, Validator.fullName(name));
        ok &= FormUtils.showError(binding.tilPhone, Validator.phone(phone));
        if (!ok) {
            FormUtils.focusFirstError(binding.tilName, binding.tilPhone);
            return;
        }

        // Nothing changed → no need to contact the server
        if (name.equals(original.getFullName()) && phone.equals(original.getPhone())) {
            showMessage(R.string.edit_profile_no_changes);
            return;
        }

        setLoading(true);
        userRepository.updateDetails(name, phone, new DataCallback<User>() {
            @Override
            public void onSuccess(User user) {
                Toast.makeText(EditProfileActivity.this, R.string.edit_profile_saved, Toast.LENGTH_SHORT).show();
                finish();     // ProfileActivity.onResume shows the new details
            }

            @Override
            public void onError(@NonNull String message) {
                setLoading(false);
                showMessage(message);
            }
        });
    }

    private void setLoading(boolean loading) {
        binding.btnSave.setEnabled(!loading);
        if (loading) binding.loading.show(); else binding.loading.hide();
    }
}
