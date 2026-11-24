package com.parkease.app.ui.customer.vehicle;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.parkease.app.R;
import com.parkease.app.databinding.ActivityAddEditVehicleBinding;
import com.parkease.app.models.Vehicle;
import com.parkease.app.repository.DataCallback;
import com.parkease.app.repository.VehicleRepository;
import com.parkease.app.ui.common.BaseActivity;
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.FormUtils;
import com.parkease.app.utils.VehicleNumberUtils;

/**
 * STEP 10 – Add a vehicle or edit an existing one.
 * Registration number (validated for Indian formats, shown as a live number plate),
 * vehicle type (Car / SUV / Bike) and an optional nickname.
 */
public class AddEditVehicleActivity extends BaseActivity {

    private static final String EXTRA_TYPE = "extra_vehicle_type";
    private static final String EXTRA_NICKNAME = "extra_vehicle_nickname";
    private static final String EXTRA_CREATED = "extra_vehicle_created";

    private ActivityAddEditVehicleBinding binding;
    private VehicleRepository repository;
    @Nullable private Vehicle existing;      // null = adding a new vehicle

    @NonNull
    public static Intent addIntent(@NonNull Context context) {
        return new Intent(context, AddEditVehicleActivity.class);
    }

    @NonNull
    public static Intent editIntent(@NonNull Context context, @NonNull Vehicle v) {
        return new Intent(context, AddEditVehicleActivity.class)
                .putExtra(Constants.EXTRA_VEHICLE_ID, v.getId())
                .putExtra(Constants.EXTRA_VEHICLE_REG, v.getRegistrationNumber())
                .putExtra(EXTRA_TYPE, v.getVehicleType())
                .putExtra(EXTRA_NICKNAME, v.getNickname())
                .putExtra(EXTRA_CREATED, v.getCreatedAt());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddEditVehicleBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        repository = new VehicleRepository(this);

        String id = getIntent().getStringExtra(Constants.EXTRA_VEHICLE_ID);
        if (id != null) {
            existing = new Vehicle();
            existing.setId(id);
            existing.setRegistrationNumber(getIntent().getStringExtra(Constants.EXTRA_VEHICLE_REG));
            existing.setVehicleType(getIntent().getStringExtra(EXTRA_TYPE));
            existing.setNickname(getIntent().getStringExtra(EXTRA_NICKNAME));
            existing.setCreatedAt(getIntent().getLongExtra(EXTRA_CREATED, 0));
        }

        applyStatusBarPadding(binding.header.getRoot());
        applyStatusBarScrim(binding.statusBarScrim.getRoot());
        applyKeyboardAwarePadding(binding.scroll);
        binding.header.toolbar.setTitle(R.string.app_name);
        binding.header.toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        binding.header.toolbar.setNavigationContentDescription(R.string.action_back);
        binding.header.toolbar.setNavigationOnClickListener(v -> finish());
        binding.header.tvHeaderTitle.setText(existing == null ? R.string.vehicle_add_title : R.string.vehicle_edit_title);
        binding.header.tvHeaderSubtitle.setText(R.string.vehicle_form_subtitle);

        // Fill in when editing
        if (existing != null && savedInstanceState == null) {
            binding.etRegistration.setText(existing.getDisplayNumber());
            binding.etNickname.setText(existing.getNickname());
            String t = existing.getVehicleType();
            binding.toggleType.check(Constants.VEHICLE_BIKE.equals(t) ? R.id.btnTypeBike
                    : Constants.VEHICLE_SUV.equals(t) ? R.id.btnTypeSuv : R.id.btnTypeCar);
        }
        binding.btnDelete.setVisibility(existing == null ? View.GONE : View.VISIBLE);

        // Live number plate preview
        binding.etRegistration.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void afterTextChanged(Editable s) {
                updatePlate();
            }
        });
        updatePlate();

        binding.toggleType.addOnButtonCheckedListener((group, checkedId, isChecked) ->
                binding.tvTypeHint.setVisibility(checkedId == R.id.btnTypeSuv && isChecked ? View.VISIBLE : View.GONE));

        FormUtils.clearErrorOnType(binding.tilRegistration, binding.tilNickname);
        binding.etNickname.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                save();
                return true;
            }
            return false;
        });
        binding.btnSave.setOnClickListener(v -> save());
        binding.btnDelete.setOnClickListener(v -> {
            if (existing != null) {
                VehiclesActivity.confirmDelete(this, repository, existing, () -> {
                    Toast.makeText(this, R.string.vehicle_deleted, Toast.LENGTH_SHORT).show();
                    finish();
                }, this::showMessage);
            }
        });
    }

    private void updatePlate() {
        String text = FormUtils.text(binding.tilRegistration);
        binding.tvPlatePreview.setText(text.trim().isEmpty()
                ? getString(R.string.plate_placeholder)
                : VehicleNumberUtils.format(text));
        binding.tvPlatePreview.setAlpha(text.trim().isEmpty() ? 0.4f : 1f);
    }

    private void save() {
        hideKeyboard();
        String reg = FormUtils.text(binding.tilRegistration);
        String nickname = FormUtils.text(binding.tilNickname).trim();

        boolean ok = FormUtils.showError(binding.tilRegistration, VehicleNumberUtils.validate(reg));
        ok &= FormUtils.showError(binding.tilNickname, nickname.length() > 30 ? R.string.vehicle_err_nickname : 0);
        if (!ok) {
            FormUtils.focusFirstError(binding.tilRegistration, binding.tilNickname);
            return;
        }

        int checked = binding.toggleType.getCheckedButtonId();
        String type = checked == R.id.btnTypeBike ? Constants.VEHICLE_BIKE
                : checked == R.id.btnTypeSuv ? Constants.VEHICLE_SUV : Constants.VEHICLE_CAR;

        setLoading(true);
        repository.saveVehicle(existing, reg, type, nickname, new DataCallback<Vehicle>() {
            @Override
            public void onSuccess(Vehicle vehicle) {
                Toast.makeText(AddEditVehicleActivity.this,
                        existing == null ? R.string.vehicle_added : R.string.vehicle_updated, Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK, new Intent().putExtra(Constants.EXTRA_VEHICLE_ID, vehicle.getId()));
                finish();
            }

            @Override
            public void onError(@NonNull String message) {
                setLoading(false);
                if (message.equals(getString(R.string.vehicle_err_duplicate))
                        || message.equals(getString(R.string.vehicle_err_format))
                        || message.equals(getString(R.string.vehicle_err_state))) {
                    binding.tilRegistration.setError(message);
                    binding.etRegistration.requestFocus();
                } else {
                    showMessage(message);
                }
            }
        });
    }

    private void setLoading(boolean loading) {
        binding.btnSave.setEnabled(!loading);
        if (loading) binding.loading.show(); else binding.loading.hide();
    }
}
