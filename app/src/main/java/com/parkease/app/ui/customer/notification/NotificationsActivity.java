package com.parkease.app.ui.customer.notification;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DividerItemDecoration;

import com.parkease.app.R;
import com.parkease.app.adapters.NotificationAdapter;
import com.parkease.app.databinding.ActivityNotificationsBinding;
import com.parkease.app.models.AppNotification;
import com.parkease.app.repository.DataCallback;
import com.parkease.app.repository.NotificationRepository;
import com.parkease.app.repository.Subscription;
import com.parkease.app.ui.common.BaseActivity;

import java.util.List;

/** STEP 7 – Notification inbox (live from Firebase). Tapping one marks it as read. */
public class NotificationsActivity extends BaseActivity {

    private ActivityNotificationsBinding binding;
    private NotificationRepository repository;
    private NotificationAdapter adapter;
    private Subscription subscription = Subscription.EMPTY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNotificationsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        repository = new NotificationRepository(this);

        applyStatusBarPadding(binding.header.getRoot());
        applyStatusBarScrim(binding.statusBarScrim.getRoot());
        applyNavigationBarPadding(binding.getRoot());
        binding.header.toolbar.setTitle(R.string.app_name);
        binding.header.toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        binding.header.toolbar.setNavigationContentDescription(R.string.action_back);
        binding.header.toolbar.setNavigationOnClickListener(v -> finish());
        binding.header.tvHeaderTitle.setText(R.string.notifications_title);
        binding.header.tvHeaderSubtitle.setText(R.string.notifications_subtitle);

        adapter = new NotificationAdapter(n -> {
            if (!n.isRead()) repository.markRead(n.getId());
        });
        binding.rvNotifications.setAdapter(adapter);
        binding.rvNotifications.addItemDecoration(
                new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        binding.stateView.showLoading();
    }

    @Override
    protected void onStart() {
        super.onStart();
        subscription = repository.observeMyNotifications(new DataCallback<List<AppNotification>>() {
            @Override
            public void onSuccess(List<AppNotification> list) {
                adapter.submitList(list);
                if (list.isEmpty()) {
                    binding.stateView.showEmpty(R.drawable.ic_notifications,
                            getString(R.string.notifications_empty_title),
                            getString(R.string.notifications_empty_msg), null, null);
                } else {
                    binding.stateView.hide();
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
}
