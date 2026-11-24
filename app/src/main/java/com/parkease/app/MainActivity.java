package com.parkease.app;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.google.firebase.FirebaseApp;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import com.parkease.app.database.DBHelper;
import com.parkease.app.databinding.ActivityMainBinding;
import com.parkease.app.databinding.ItemSetupCheckBinding;
import com.parkease.app.utils.Constants;

import java.util.ArrayList;
import java.util.List;

/**
 * STEP 2 – Setup check screen.
 * Proves that the project builds and that every library works:
 * Material 3 theme, Firebase, SQLite, ZXing (QR) and MPAndroidChart.
 * This screen is replaced by SplashActivity in Step 3.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Draw behind the status bar (required on Android 15) with white status icons
        EdgeToEdge.enable(this,
                SystemBarStyle.dark(Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets();

        checkFirebase();
        checkSqlite();
        checkQrCode();
        checkChart();
    }

    // ---------------------------------------------------------------- Firebase
    private void checkFirebase() {
        if (FirebaseApp.getApps(this).isEmpty()) {
            showResult(binding.checkFirebase, false,
                    getString(R.string.check_firebase),
                    getString(R.string.check_firebase_missing));
        } else {
            String projectId = FirebaseApp.getInstance().getOptions().getProjectId();
            showResult(binding.checkFirebase, true,
                    getString(R.string.check_firebase),
                    getString(R.string.check_firebase_ok, projectId));
        }
    }

    // ---------------------------------------------------------------- SQLite
    private void checkSqlite() {
        try {
            int tables = DBHelper.getInstance(this).countTables();
            showResult(binding.checkSqlite, tables == 5,
                    getString(R.string.check_sqlite),
                    getString(R.string.check_sqlite_ok, tables));
        } catch (Exception e) {
            showResult(binding.checkSqlite, false,
                    getString(R.string.check_sqlite), e.getMessage());
        }
    }

    // ---------------------------------------------------------------- ZXing QR
    private void checkQrCode() {
        try {
            BarcodeEncoder encoder = new BarcodeEncoder();
            Bitmap qr = encoder.encodeBitmap(Constants.QR_PREFIX + "|TEST-BOOKING",
                    BarcodeFormat.QR_CODE, 400, 400);
            binding.imgQr.setImageBitmap(qr);
            showResult(binding.checkQr, true,
                    getString(R.string.check_qr), getString(R.string.check_qr_ok));
        } catch (Exception e) {
            showResult(binding.checkQr, false,
                    getString(R.string.check_qr), e.getMessage());
        }
    }

    // ---------------------------------------------------------------- MPAndroidChart
    private void checkChart() {
        List<PieEntry> entries = new ArrayList<>();
        entries.add(new PieEntry(24f, getString(R.string.status_available)));
        entries.add(new PieEntry(6f, getString(R.string.status_reserved)));
        entries.add(new PieEntry(8f, getString(R.string.status_occupied)));
        entries.add(new PieEntry(2f, getString(R.string.status_maintenance)));

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(
                color(R.color.status_available),
                color(R.color.status_reserved),
                color(R.color.status_occupied),
                color(R.color.status_maintenance));
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(12f);
        dataSet.setSliceSpace(2f);

        binding.pieChart.setData(new PieData(dataSet));
        binding.pieChart.getDescription().setEnabled(false);
        binding.pieChart.setHoleRadius(55f);
        binding.pieChart.setTransparentCircleRadius(60f);
        binding.pieChart.setCenterText(getString(R.string.chart_center));
        binding.pieChart.setCenterTextColor(color(R.color.navy));
        binding.pieChart.setDrawEntryLabels(false);
        binding.pieChart.getLegend().setWordWrapEnabled(true);
        binding.pieChart.animateY(800);
        binding.pieChart.invalidate();

        showResult(binding.checkChart, true,
                getString(R.string.check_chart), getString(R.string.check_chart_ok));
    }

    // ---------------------------------------------------------------- helpers

    /** Pushes the header below the status bar and the content above the navigation bar. */
    private void applySystemBarInsets() {
        final int headerTop = binding.header.getPaddingTop();
        final int contentBottom = binding.contentRoot.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.header.setPadding(binding.header.getPaddingLeft(), headerTop + bars.top,
                    binding.header.getPaddingRight(), binding.header.getPaddingBottom());
            binding.contentRoot.setPadding(bars.left, 0, bars.right, contentBottom + bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void showResult(ItemSetupCheckBinding row, boolean ok, String title, String detail) {
        row.tvCheckTitle.setText(title);
        row.tvCheckDetail.setText(detail);
        @DrawableRes int icon = ok ? R.drawable.ic_check_circle : R.drawable.ic_error;
        @ColorRes int tint = ok ? R.color.status_available : R.color.status_reserved;
        row.imgCheckIcon.setImageResource(icon);
        row.imgCheckIcon.setColorFilter(color(tint));
    }

    private int color(@ColorRes int res) {
        return ContextCompat.getColor(this, res);
    }
}
