package com.parkease.app.ui.customer.vehicle;

import android.os.Bundle;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.parkease.app.R;
import com.parkease.app.adapters.SpaceItemDecoration;
import com.parkease.app.adapters.VehicleAdapter;
import com.parkease.app.databinding.ActivityVehiclesBinding;
import com.parkease.app.models.Vehicle;
import com.parkease.app.repository.DataCallback;
import com.parkease.app.repository.Subscription;
import com.parkease.app.repository.VehicleRepository;
import com.parkease.app.ui.common.BaseActivity;

import java.util.List;

/**
 * STEP 10 – My vehicles: live list from Firebase, add (button), edit (tap), delete (⋮ menu).
 */
public class VehiclesActivity extends BaseActivity implements VehicleAdapter.Listener {

    private ActivityVehiclesBinding binding;
    private VehicleRepository repository;
    private VehicleAdapter adapter;
    private Subscription subscription = Subscription.EMPTY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVehiclesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        repository = new VehicleRepository(this);

        applyStatusBarPadding(binding.header.getRoot());
        applyStatusBarScrim(binding.statusBarScrim.getRoot());
        applyNavigationBarPadding(binding.getRoot());
        binding.header.toolbar.setTitle(R.string.app_name);
        binding.header.toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        binding.header.toolbar.setNavigationContentDescription(R.string.action_back);
        binding.header.toolbar.setNavigationOnClickListener(v -> finish());
        binding.header.tvHeaderTitle.setText(R.string.vehicles_title);
        binding.header.tvHeaderSubtitle.setText(R.string.vehicles_subtitle);
        // No overlapping card here → smaller header
        binding.header.getRoot().setPaddingRelative(0, binding.header.getRoot().getPaddingTop(), 0,
                getResources().getDimensionPixelSize(R.dimen.space_xl));

        adapter = new VehicleAdapter(this);
        binding.rvVehicles.setAdapter(adapter);
        binding.rvVehicles.addItemDecoration(new SpaceItemDecoration(
                getResources().getDimensionPixelSize(R.dimen.card_gap), false));

        binding.fabAdd.setOnClickListener(v -> startActivity(AddEditVehicleActivity.addIntent(this)));
        binding.stateView.showLoading();
    }

    @Override
    protected void onStart() {
        super.onStart();
        subscription = repository.observeMyVehicles(new DataCallback<List<Vehicle>>() {
            @Override
            public void onSuccess(List<Vehicle> vehicles) {
                adapter.submitList(vehicles);
                if (vehicles.isEmpty()) {
                    binding.stateView.showEmpty(R.drawable.ic_car, getString(R.string.vehicles_empty_title),
                            getString(R.string.vehicles_empty_msg), getString(R.string.vehicles_add),
                            v -> startActivity(AddEditVehicleActivity.addIntent(VehiclesActivity.this)));
                    binding.fabAdd.hide();
                } else {
                    binding.stateView.hide();
                    binding.fabAdd.show();
                }
            }

            @Override
            public void onError(@NonNull String message) {
                binding.stateView.showError(message, v -> recreate());
            }
        });
    }

    @Override
    protected void onStop() {
        subscription.cancel();
        super.onStop();
    }

    @Override
    public void onEdit(@NonNull Vehicle vehicle) {
        startActivity(AddEditVehicleActivity.editIntent(this, vehicle));
    }

    @Override
    public void onDelete(@NonNull Vehicle vehicle) {
        confirmDelete(this, repository, vehicle, () -> showMessage(R.string.vehicle_deleted), this::showMessage);
    }

    /** Shared "Delete GJ 01 AB 1234?" dialog (also used by the edit screen). */
    static void confirmDelete(@NonNull BaseActivity activity, @NonNull VehicleRepository repo,
                              @NonNull Vehicle vehicle, @NonNull Runnable onDeleted,
                              @NonNull java.util.function.Consumer<String> onError) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.vehicle_delete_title, vehicle.getDisplayNumber()))
                .setMessage(R.string.vehicle_delete_msg)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.vehicle_delete, (d, w) ->
                        repo.deleteVehicle(vehicle, new DataCallback<Void>() {
                            @Override
                            public void onSuccess(Void data) {
                                onDeleted.run();
                            }

                            @Override
                            public void onError(@NonNull String message) {
                                onError.accept(message);
                            }
                        }))
                .show();
    }
}
