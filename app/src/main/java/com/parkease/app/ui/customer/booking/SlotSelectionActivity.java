package com.parkease.app.ui.customer.booking;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;

import com.google.android.material.chip.Chip;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.parkease.app.R;
import com.parkease.app.adapters.SlotGridAdapter;
import com.parkease.app.databinding.ActivitySlotSelectionBinding;
import com.parkease.app.models.Booking;
import com.parkease.app.models.ParkingLocation;
import com.parkease.app.models.ParkingSlot;
import com.parkease.app.models.ScheduleBlock;
import com.parkease.app.models.TimeWindow;
import com.parkease.app.repository.DataCallback;
import com.parkease.app.repository.LocationRepository;
import com.parkease.app.repository.SlotRepository;
import com.parkease.app.repository.Subscription;
import com.parkease.app.ui.common.BaseActivity;
import com.parkease.app.ui.components.ParkingSlotView;
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.FormatUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * STEP 9 – Interactive parking layout.
 *
 * 1. The customer picks vehicle type, date, start time and duration.
 * 2. The grid shows every slot of the chosen floor, coloured for THAT time:
 *    green available · yellow reserved · red occupied · grey maintenance.
 *    Colours update live from Firebase (slots + slotSchedule); pull down or tap ⟳ to refresh.
 * 3. Only green slots can be selected; the panel at the bottom shows the slot's details.
 * 4. "Reserve slot" asks the SERVER to hold the slot (SlotRepository.holdSlot). The security
 *    rules reject the request if anyone else already has any part of that time, so
 *    conflicting bookings are impossible even if two people tap at the same moment.
 */
public class SlotSelectionActivity extends BaseActivity implements SlotGridAdapter.Listener {

    private static final long TICK_MS = 30_000;   // holds expire → re-colour every 30 s

    private ActivitySlotSelectionBinding binding;
    private LocationRepository locationRepository;
    private SlotRepository slotRepository;
    private SlotGridAdapter adapter;

    private Subscription locationSub = Subscription.EMPTY;
    private Subscription slotsSub = Subscription.EMPTY;
    private Subscription scheduleSub = Subscription.EMPTY;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String locationId;
    @Nullable private ParkingLocation location;
    private List<ParkingSlot> slots = new ArrayList<>();
    private Map<String, Map<String, ScheduleBlock>> schedule = new HashMap<>();
    private boolean slotsLoaded = false;

    private TimeWindow window = TimeWindow.nextAvailable();
    private String vehicleType = Constants.VEHICLE_CAR;
    @Nullable private String selectedFloor;
    @Nullable private ParkingSlot selectedSlot;
    private int timeError = 0;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            render();
            handler.postDelayed(this, TICK_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        locationId = getIntent().getStringExtra(Constants.EXTRA_LOCATION_ID);
        if (locationId == null) {
            finish();
            return;
        }
        binding = ActivitySlotSelectionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        locationRepository = new LocationRepository(this);
        slotRepository = new SlotRepository(this);

        applyStatusBarPadding(binding.header.getRoot());
        applyStatusBarScrim(binding.statusBarScrim.getRoot());
        applyNavigationBarPadding(binding.bottomPanel);

        // Compact header
        binding.header.toolbar.setTitle(R.string.slots_title);
        binding.header.toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        binding.header.toolbar.setNavigationContentDescription(R.string.action_back);
        binding.header.toolbar.setNavigationOnClickListener(v -> finish());
        binding.header.toolbar.inflateMenu(R.menu.menu_slot_selection);
        binding.header.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_refresh) {
                refresh(true);
                return true;
            }
            return false;
        });
        binding.header.tvHeaderTitle.setTextAppearance(R.style.TextAppearance_ParkEase_TitleLarge);
        binding.header.tvHeaderSubtitle.setVisibility(View.GONE);

        // Grid
        int columns = getResources().getInteger(R.integer.slot_grid_columns);
        adapter = new SlotGridAdapter(this);
        GridLayoutManager glm = new GridLayoutManager(this, columns);
        glm.setSpanSizeLookup(adapter.spanSizeLookup(columns));
        binding.rvSlots.setLayoutManager(glm);
        binding.rvSlots.setAdapter(adapter);
        binding.swipeRefresh.setColorSchemeResources(R.color.brand_secondary);
        binding.swipeRefresh.setOnRefreshListener(() -> refresh(true));

        // Pickers
        binding.toggleVehicle.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked) return;
            vehicleType = id == R.id.btnVehicleBike ? Constants.VEHICLE_BIKE : Constants.VEHICLE_CAR;
            selectedFloor = null;
            clearSelection();
            render();
        });
        binding.btnDate.setOnClickListener(v -> pickDate());
        binding.btnTime.setOnClickListener(v -> pickTime());
        binding.btnDuration.setOnClickListener(v -> pickDuration());
        binding.btnReserve.setOnClickListener(v -> reserve());

        binding.stateView.showLoading();
        updateTimeButtons();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (binding == null) return;
        subscribe();
        handler.postDelayed(tick, TICK_MS);
    }

    @Override
    protected void onStop() {
        unsubscribe();
        handler.removeCallbacksAndMessages(null);
        super.onStop();
    }

    // ================================================================== Firebase

    private void subscribe() {
        locationSub = locationRepository.observeLocation(locationId, new DataCallback<ParkingLocation>() {
            @Override
            public void onSuccess(ParkingLocation data) {
                if (data == null || !data.isActive()) {
                    binding.stateView.showEmpty(R.drawable.ic_location, getString(R.string.details_not_found),
                            null, getString(R.string.action_back), v -> finish());
                    return;
                }
                boolean first = location == null;
                location = data;
                binding.header.tvHeaderTitle.setText(data.getName());
                if (first) setupVehicleToggle(data);
                validateWindow();
                render();
            }

            @Override
            public void onError(@NonNull String message) {
                binding.stateView.showError(message, v -> refresh(false));
            }
        });

        slotsSub = slotRepository.observeSlots(locationId, new DataCallback<List<ParkingSlot>>() {
            @Override
            public void onSuccess(List<ParkingSlot> data) {
                slots = data;
                slotsLoaded = true;
                binding.swipeRefresh.setRefreshing(false);
                render();
            }

            @Override
            public void onError(@NonNull String message) {
                binding.swipeRefresh.setRefreshing(false);
                binding.stateView.showError(message, v -> refresh(false));
            }
        });

        subscribeSchedule();
    }

    /** The schedule is per day, so it is re-subscribed whenever the date changes. */
    private void subscribeSchedule() {
        scheduleSub.cancel();
        schedule = new HashMap<>();
        scheduleSub = slotRepository.observeSchedule(locationId, window.dateKey(),
                new DataCallback<Map<String, Map<String, ScheduleBlock>>>() {
                    @Override
                    public void onSuccess(Map<String, Map<String, ScheduleBlock>> data) {
                        schedule = data;
                        render();
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        binding.stateView.showError(message, v -> refresh(false));
                    }
                });
    }

    private void unsubscribe() {
        locationSub.cancel();
        slotsSub.cancel();
        scheduleSub.cancel();
    }

    /** Drops the listeners and asks Firebase again for fresh data. */
    private void refresh(boolean showMessage) {
        binding.swipeRefresh.setRefreshing(true);
        unsubscribe();
        subscribe();
        if (showMessage) Snackbar.make(binding.getRoot(), R.string.slots_refreshed, Snackbar.LENGTH_SHORT).show();
    }

    // ================================================================== drawing the layout

    private void render() {
        if (binding == null || location == null || !slotsLoaded) return;

        // Slots for the chosen vehicle type
        List<ParkingSlot> forVehicle = new ArrayList<>();
        for (ParkingSlot s : slots) if (vehicleType.equals(s.getVehicleType())) forVehicle.add(s);

        if (slots.isEmpty()) {
            binding.stateView.showEmpty(R.drawable.ic_car, getString(R.string.slots_none_title),
                    getString(R.string.slots_none_msg), null, null);
            return;
        }
        if (forVehicle.isEmpty()) {
            binding.stateView.showEmpty(R.drawable.ic_car, getString(R.string.slots_none_title),
                    getString(R.string.slots_none_vehicle, vehicleLabel().toLowerCase(Locale.ROOT)), null, null);
            return;
        }
        binding.stateView.hide();

        // Work out every slot's colour for the chosen time, grouped by floor
        Map<String, List<ParkingSlot>> byFloor = new LinkedHashMap<>();
        Map<String, Integer> freeByFloor = new HashMap<>();
        Map<String, ParkingSlotView.Status> statuses = new HashMap<>();
        for (ParkingSlot s : forVehicle) {
            String floor = s.getFloor() == null ? "G" : s.getFloor();
            byFloor.computeIfAbsent(floor, f -> new ArrayList<>()).add(s);
            ParkingSlotView.Status st = SlotRepository.statusFor(s, schedule.get(s.getId()), window);
            statuses.put(s.getId(), st);
            if (st == ParkingSlotView.Status.AVAILABLE) freeByFloor.merge(floor, 1, Integer::sum);
        }
        if (selectedFloor == null || !byFloor.containsKey(selectedFloor)) {
            selectedFloor = byFloor.keySet().iterator().next();
        }
        buildFloorChips(byFloor, freeByFloor);

        // Grid items for the chosen floor: "Zone A · 12 free" heading, then its slots
        List<ParkingSlot> floorSlots = byFloor.get(selectedFloor);
        Map<String, List<ParkingSlot>> byZone = new LinkedHashMap<>();
        for (ParkingSlot s : Objects.requireNonNull(floorSlots)) {
            byZone.computeIfAbsent(s.getZone() == null ? "-" : s.getZone(), z -> new ArrayList<>()).add(s);
        }
        List<SlotGridAdapter.Item> items = new ArrayList<>();
        List<String> zones = new ArrayList<>(byZone.keySet());
        Collections.sort(zones);
        for (String zone : zones) {
            List<ParkingSlot> zoneSlots = byZone.get(zone);
            int free = 0;
            for (ParkingSlot s : Objects.requireNonNull(zoneSlots)) {
                if (statuses.get(s.getId()) == ParkingSlotView.Status.AVAILABLE) free++;
            }
            items.add(SlotGridAdapter.Item.header(getString(R.string.slots_zone_header, zone, free)));
            for (ParkingSlot s : zoneSlots) items.add(SlotGridAdapter.Item.slot(s, Objects.requireNonNull(statuses.get(s.getId()))));
        }
        adapter.submitList(items);

        // The selected slot was just taken (live update) → tell the user
        if (selectedSlot != null && statuses.get(selectedSlot.getId()) != ParkingSlotView.Status.AVAILABLE) {
            String number = selectedSlot.getSlotNumber();
            clearSelection();
            Snackbar.make(binding.getRoot(), getString(R.string.slots_selection_lost, number), Snackbar.LENGTH_LONG).show();
        } else if (selectedSlot != null) {
            showSelected(selectedSlot);      // refresh price/time text
        }
    }

    private void buildFloorChips(@NonNull Map<String, List<ParkingSlot>> byFloor,
                                 @NonNull Map<String, Integer> freeByFloor) {
        binding.floorScroll.setVisibility(byFloor.size() > 1 ? View.VISIBLE : View.GONE);
        binding.chipFloors.setOnCheckedStateChangeListener(null);
        binding.chipFloors.removeAllViews();
        for (Map.Entry<String, List<ParkingSlot>> e : byFloor.entrySet()) {
            Chip chip = new Chip(this);
            chip.setId(View.generateViewId());
            chip.setCheckable(true);
            String label = e.getValue().get(0).getFloorLabel();
            Integer free = freeByFloor.get(e.getKey());
            chip.setText(getString(R.string.slots_floor_chip, label, free == null ? 0 : free));
            chip.setTag(e.getKey());
            binding.chipFloors.addView(chip);
            if (e.getKey().equals(selectedFloor)) chip.setChecked(true);
        }
        binding.chipFloors.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            Chip chip = group.findViewById(ids.get(0));
            if (chip != null && !Objects.equals(chip.getTag(), selectedFloor)) {
                selectedFloor = (String) chip.getTag();
                render();
            }
        });
    }

    // ================================================================== selection

    @Override
    public void onSlotSelected(@NonNull ParkingSlot slot) {
        if (timeError != 0) {                       // fix the time first
            Snackbar.make(binding.getRoot(), timeError, Snackbar.LENGTH_LONG).show();
            return;
        }
        selectedSlot = slot;
        adapter.setSelectedSlotId(slot.getId());
        showSelected(slot);
    }

    @Override
    public void onUnavailableSlotClick(@NonNull ParkingSlot slot, @NonNull ParkingSlotView.Status status) {
        int why = status == ParkingSlotView.Status.OCCUPIED ? R.string.slot_why_occupied
                : status == ParkingSlotView.Status.MAINTENANCE ? R.string.slot_why_maintenance
                : R.string.slot_why_reserved;
        Snackbar.make(binding.getRoot(), getString(why, slot.getSlotNumber()), Snackbar.LENGTH_SHORT).show();
    }

    private void showSelected(@NonNull ParkingSlot slot) {
        binding.tvHint.setVisibility(View.GONE);
        binding.selectedGroup.setVisibility(View.VISIBLE);
        binding.tvSelectedTitle.setText(getString(R.string.slots_selected_title, slot.getSlotNumber()));
        binding.tvSelectedPlace.setText(getString(R.string.slots_selected_place, slot.getFloorLabel(), slot.getZone()));
        binding.tvSelectedInfo.setText(getString(R.string.slots_selected_info, vehicleLabel(),
                FormatUtils.money(slot.getPricePerHour()), durationLabel(window.getMinutes())));
        binding.tvSelectedId.setText(getString(R.string.slots_selected_id, slot.getId()));
        binding.tvTotal.setText(FormatUtils.money(slot.getPricePerHour() * window.getHours()));
        binding.btnReserve.setEnabled(timeError == 0);
    }

    private void clearSelection() {
        selectedSlot = null;
        if (adapter != null) adapter.setSelectedSlotId(null);
        binding.selectedGroup.setVisibility(View.GONE);
        binding.tvHint.setVisibility(View.VISIBLE);
    }

    // ================================================================== reserve (server-verified)

    private void reserve() {
        if (location == null || selectedSlot == null || timeError != 0) return;
        ParkingSlot slot = selectedSlot;
        TimeWindow chosen = window;
        binding.loading.show();
        slotRepository.holdSlot(location, slot, chosen, new DataCallback<Booking>() {
            @Override
            public void onSuccess(Booking booking) {
                binding.loading.hide();
                clearSelection();
                showHoldConfirmation(booking, chosen);
            }

            @Override
            public void onError(@NonNull String message) {
                binding.loading.hide();
                new MaterialAlertDialogBuilder(SlotSelectionActivity.this)
                        .setMessage(message)
                        .setPositiveButton(R.string.action_ok, null)
                        .show();
            }
        });
    }

    private void showHoldConfirmation(@NonNull Booking booking, @NonNull TimeWindow chosen) {
        String message = getString(R.string.hold_message,
                booking.getLocationName(),
                FormatUtils.timeRange(booking.getStartTime(), booking.getEndTime()),
                FormatUtils.money(booking.getAmount()),
                booking.getBookingCode(),
                FormatUtils.time(booking.getHoldUntil()));
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.hold_title, booking.getSlotNumber()))
                .setMessage(message)
                .setCancelable(false)
                .setNegativeButton(R.string.hold_release, (d, w) ->
                        slotRepository.releaseHold(booking, chosen, new DataCallback<Void>() {
                            @Override
                            public void onSuccess(Void data) {
                                Snackbar.make(binding.getRoot(), R.string.hold_released, Snackbar.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onError(@NonNull String error) {
                                Snackbar.make(binding.getRoot(), error, Snackbar.LENGTH_LONG).show();
                            }
                        }))
                .setPositiveButton(R.string.hold_keep, null)
                .show();
    }

    // ================================================================== time & vehicle pickers

    private void setupVehicleToggle(@NonNull ParkingLocation p) {
        binding.btnVehicleCar.setEnabled(p.acceptsCars());
        binding.btnVehicleBike.setEnabled(p.acceptsBikes());
        binding.toggleVehicle.setVisibility(p.acceptsCars() && p.acceptsBikes() ? View.VISIBLE : View.GONE);
        vehicleType = p.acceptsCars() ? Constants.VEHICLE_CAR : Constants.VEHICLE_BIKE;
        binding.toggleVehicle.check(p.acceptsCars() ? R.id.btnVehicleCar : R.id.btnVehicleBike);
    }

    private void pickDate() {
        long todayUtc = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setStart(todayUtc)
                .setEnd(LocalDate.now().plusDays(6).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                .setValidator(DateValidatorPointForward.from(todayUtc))
                .build();
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.time_pick_date)
                .setSelection(window.getDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                .setCalendarConstraints(constraints)
                .build();
        picker.addOnPositiveButtonClickListener(selection -> {
            LocalDate date = Instant.ofEpochMilli(selection).atZone(ZoneOffset.UTC).toLocalDate();
            boolean dayChanged = !date.equals(window.getDate());
            setWindow(window.withDate(date));
            if (dayChanged) subscribeSchedule();
        });
        picker.show(getSupportFragmentManager(), "date");
    }

    private void pickTime() {
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H)
                .setHour(window.getStart().getHour())
                .setMinute(window.getStart().getMinute())
                .setTitleText(R.string.time_pick_start)
                .build();
        picker.addOnPositiveButtonClickListener(v -> {
            // Round UP to the next 30-minute mark (bookings use 30-minute blocks)
            int total = picker.getHour() * 60 + picker.getMinute();
            int rounded = ((total + Constants.BLOCK_MINUTES - 1) / Constants.BLOCK_MINUTES) * Constants.BLOCK_MINUTES;
            if (rounded >= 24 * 60) rounded = 24 * 60 - Constants.BLOCK_MINUTES;
            setWindow(window.withStart(LocalTime.of(rounded / 60, rounded % 60)));
        });
        picker.show(getSupportFragmentManager(), "time");
    }

    private void pickDuration() {
        int steps = TimeWindow.MAX_MINUTES / Constants.BLOCK_MINUTES;   // 30 min … 12 h
        String[] labels = new String[steps];
        int checked = 0;
        for (int i = 0; i < steps; i++) {
            int minutes = (i + 1) * Constants.BLOCK_MINUTES;
            labels[i] = durationLabel(minutes);
            if (minutes == window.getMinutes()) checked = i;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.time_pick_duration)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    setWindow(window.withMinutes((which + 1) * Constants.BLOCK_MINUTES));
                    dialog.dismiss();
                })
                .show();
    }

    private void setWindow(@NonNull TimeWindow newWindow) {
        window = newWindow;
        updateTimeButtons();
        validateWindow();
        render();
    }

    private void validateWindow() {
        timeError = location == null ? 0 : window.validate(location.getOpenTime(), location.getCloseTime());
        binding.tvTimeError.setVisibility(timeError == 0 ? View.GONE : View.VISIBLE);
        if (timeError != 0) {
            binding.tvTimeError.setText(timeError);
            clearSelection();
        }
    }

    private void updateTimeButtons() {
        binding.btnDate.setText(FormatUtils.day(window.startMillis()));
        binding.btnTime.setText(FormatUtils.time(window.startMillis()));
        binding.btnDuration.setText(durationLabel(window.getMinutes()));
    }

    @NonNull
    private String durationLabel(int minutes) {
        if (minutes == 30) return getString(R.string.duration_minutes);
        double hours = minutes / 60.0;
        String h = hours == Math.floor(hours) ? String.valueOf((int) hours) : String.valueOf(hours);
        return getString(R.string.duration_hours, h);
    }

    @NonNull
    private String vehicleLabel() {
        return getString(Constants.VEHICLE_BIKE.equals(vehicleType) ? R.string.vehicle_bike : R.string.vehicle_car);
    }
}
