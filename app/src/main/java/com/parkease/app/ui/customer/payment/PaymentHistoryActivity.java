package com.parkease.app.ui.customer.payment;

import android.os.Bundle;

import androidx.annotation.NonNull;

import com.parkease.app.R;
import com.parkease.app.adapters.PaymentAdapter;
import com.parkease.app.adapters.SpaceItemDecoration;
import com.parkease.app.databinding.ActivityPaymentHistoryBinding;
import com.parkease.app.models.Payment;
import com.parkease.app.repository.DataCallback;
import com.parkease.app.repository.PaymentRepository;
import com.parkease.app.repository.Subscription;
import com.parkease.app.ui.common.BaseActivity;

import java.util.List;

/**
 * STEP 12 – Payment history: every simulated payment (successful, pending cash, failed,
 * refunded), newest first, updated live. Tapping one opens its receipt.
 */
public class PaymentHistoryActivity extends BaseActivity {

    private ActivityPaymentHistoryBinding binding;
    private PaymentRepository repository;
    private PaymentAdapter adapter;
    private Subscription subscription = Subscription.EMPTY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPaymentHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        repository = new PaymentRepository(this);

        applyStatusBarPadding(binding.header.getRoot());
        applyStatusBarScrim(binding.statusBarScrim.getRoot());
        applyNavigationBarPadding(binding.rvPayments);
        binding.header.toolbar.setTitle(R.string.app_name);
        binding.header.toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        binding.header.toolbar.setNavigationContentDescription(R.string.action_back);
        binding.header.toolbar.setNavigationOnClickListener(v -> finish());
        binding.header.tvHeaderTitle.setText(R.string.pay_history_title);
        binding.header.tvHeaderSubtitle.setText(R.string.pay_history_subtitle);
        // No overlapping card here → smaller header
        binding.header.getRoot().setPaddingRelative(0, binding.header.getRoot().getPaddingTop(), 0,
                getResources().getDimensionPixelSize(R.dimen.space_xl));

        adapter = new PaymentAdapter(payment ->
                startActivity(PaymentResultActivity.intent(this, payment.getTransactionId())));
        binding.rvPayments.setAdapter(adapter);
        binding.rvPayments.addItemDecoration(new SpaceItemDecoration(
                getResources().getDimensionPixelSize(R.dimen.card_gap), false));
        binding.stateView.showLoading();
    }

    @Override
    protected void onStart() {
        super.onStart();
        subscription = repository.observeMyPayments(new DataCallback<List<Payment>>() {
            @Override
            public void onSuccess(List<Payment> payments) {
                adapter.submitList(payments);
                if (payments.isEmpty()) {
                    binding.stateView.showEmpty(R.drawable.ic_receipt, getString(R.string.pay_history_empty_title),
                            getString(R.string.pay_history_empty_msg), null, null);
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
