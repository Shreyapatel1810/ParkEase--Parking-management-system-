package com.parkease.app.ui.admin.scan;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputFilter;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;
import com.parkease.app.R;
import com.parkease.app.databinding.ActivityScanTicketBinding;
import com.parkease.app.ui.common.BaseActivity;
import com.parkease.app.utils.TicketCodec;

import java.util.Collections;

/**
 * STEP 13 – Gate scanner for staff (opened from the admin dashboard).
 *
 * Uses ZXing (zxing-android-embedded) to read QR codes continuously. A scanned code is only
 * accepted if it has the ParkEase ticket format; then VerifyTicketActivity looks the booking
 * up in Firebase. Nothing written in the QR is shown or trusted directly.
 *
 * Camera permission: declared in AndroidManifest.xml and asked for at runtime here.
 */
public class ScanTicketActivity extends BaseActivity {

    private ActivityScanTicketBinding binding;
    private boolean torchOn;
    /** Ignores the next frames after a scan, so one ticket isn't handled several times. */
    private boolean handling;

    private final ActivityResultLauncher<String> cameraPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCamera();
                else showPermissionNeeded(true);
            });

    private final BarcodeCallback callback = new BarcodeCallback() {
        @Override
        public void barcodeResult(BarcodeResult result) {
            if (handling || result == null) return;
            handling = true;
            TicketCodec.Scanned scanned = TicketCodec.parse(result.getText());
            if (scanned == null) {
                Snackbar.make(binding.getRoot(), R.string.gate_not_parkease, Snackbar.LENGTH_SHORT).show();
                binding.getRoot().postDelayed(() -> handling = false, 2000);   // keep scanning
                return;
            }
            binding.barcodeView.pause();
            startActivity(VerifyTicketActivity.forScan(ScanTicketActivity.this, scanned.bookingId, scanned.token));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityScanTicketBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        applyStatusBarPadding(binding.toolbar);
        applyNavigationBarPadding(binding.bottomBar);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        binding.barcodeView.getBarcodeView().setDecoderFactory(
                new DefaultDecoderFactory(Collections.singletonList(BarcodeFormat.QR_CODE)));
        binding.barcodeView.setStatusText(getString(R.string.gate_scan_hint));
        binding.barcodeView.decodeContinuous(callback);

        binding.btnTorch.setOnClickListener(v -> toggleTorch());
        binding.btnManual.setOnClickListener(v -> askForCode());
        binding.btnManualFromPermission.setOnClickListener(v -> askForCode());
        binding.btnAllow.setOnClickListener(v -> {
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) || !askedBefore) {
                askedBefore = true;
                cameraPermission.launch(Manifest.permission.CAMERA);
            } else {
                // "Don't ask again" was chosen → only the Settings screen can grant it
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", getPackageName(), null)));
            }
        });
    }

    private boolean askedBefore;

    @Override
    protected void onResume() {
        super.onResume();
        handling = false;
        if (hasCamera()) {
            startCamera();
        } else if (!askedBefore) {
            askedBefore = true;
            cameraPermission.launch(Manifest.permission.CAMERA);
        } else {
            showPermissionNeeded(true);
        }
    }

    @Override
    protected void onPause() {
        binding.barcodeView.pause();
        super.onPause();
    }

    private boolean hasCamera() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void startCamera() {
        showPermissionNeeded(false);
        binding.barcodeView.resume();
    }

    private void showPermissionNeeded(boolean show) {
        binding.permissionGroup.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.bottomBar.setVisibility(show ? View.GONE : View.VISIBLE);
        if (show) binding.barcodeView.pause();
    }

    private void toggleTorch() {
        torchOn = !torchOn;
        if (torchOn) binding.barcodeView.setTorchOn();
        else binding.barcodeView.setTorchOff();
        binding.btnTorch.setText(torchOn ? R.string.gate_torch_off : R.string.gate_torch_on);
    }

    /** Fallback when a QR is damaged: staff type the booking code (then must check the plate). */
    private void askForCode() {
        TextInputLayout layout = new TextInputLayout(this);
        int pad = getResources().getDimensionPixelSize(R.dimen.screen_padding);
        layout.setPadding(pad, pad / 2, pad, 0);
        layout.setHint(R.string.gate_enter_code_hint);
        TextInputEditText input = new TextInputEditText(layout.getContext());
        input.setSingleLine(true);
        input.setFilters(new InputFilter[]{new InputFilter.AllCaps(), new InputFilter.LengthFilter(12)});
        layout.addView(input);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.gate_enter_code_title)
                .setView(layout)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.gate_find, (d, w) -> {
                    String code = input.getText() == null ? "" : input.getText().toString().trim();
                    if (!code.isEmpty()) {
                        binding.barcodeView.pause();
                        startActivity(VerifyTicketActivity.forCode(this, code));
                    }
                })
                .show();
    }
}
