package com.parkease.app.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.parkease.app.R;
import com.parkease.app.models.Booking;
import com.parkease.app.models.Payment;
import com.parkease.app.utils.Constants;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/**
 * Step 12 – unit tests for the simulated payment module.
 * (The "no duplicate successful payment" guarantee is enforced by the Firebase rules and is
 * tested by hand on the device – see the checklist.)
 */
public class PaymentRulesTest {

    @Test
    public void transactionIdHasTheExpectedFormat() {
        String id = PaymentRepository.newTransactionId();
        // TXN + yyyyMMddHHmmss + 10 random characters
        assertTrue(id, id.matches("^TXN[0-9]{14}[A-Z2-9]{10}$"));
    }

    @Test
    public void transactionIdsAreUnique() {
        long now = System.currentTimeMillis();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 10_000; i++) ids.add(PaymentRepository.newTransactionId(now));
        assertEquals(10_000, ids.size());   // same second, still no duplicates
    }

    @Test
    public void paymentStatusHelpers() {
        Payment p = new Payment();
        p.setStatus(Constants.PAYMENT_SUCCESS);
        assertTrue(p.isSuccessful());
        assertFalse(p.isFailed());
        p.setStatus(Constants.PAYMENT_FAILED);
        assertTrue(p.isFailed());
        p.setStatus(Constants.PAYMENT_PENDING);
        assertTrue(p.isPending());
        p.setStatus(Constants.PAYMENT_REFUNDED);
        assertTrue(p.isRefunded());
        assertTrue("payments are always marked simulated", new Payment().isSimulated());
    }

    @Test
    public void labelsForMethodsAndStatuses() {
        assertEquals(R.string.pay_method_upi, Payment.methodLabel(Constants.PAYMENT_UPI));
        assertEquals(R.string.pay_method_card, Payment.methodLabel(Constants.PAYMENT_CARD));
        assertEquals(R.string.pay_method_cash, Payment.methodLabel(Constants.PAYMENT_CASH));
        assertEquals(R.string.pay_status_success, Payment.statusLabel(Constants.PAYMENT_SUCCESS));
        assertEquals(R.string.pay_status_failed, Payment.statusLabel(Constants.PAYMENT_FAILED));
        assertEquals(R.string.pay_status_refunded, Payment.statusLabel(Constants.PAYMENT_REFUNDED));
        assertEquals(R.string.pay_status_pending, Payment.statusLabel(Constants.PAYMENT_PENDING));
    }

    @Test
    public void cashBookingIsRecognised() {
        Booking b = new Booking();
        b.setPaymentMethod(Constants.PAYMENT_CASH);
        assertTrue(b.isCashPayment());
        b.setPaymentMethod(Constants.PAYMENT_UPI);
        assertFalse(b.isCashPayment());
    }
}
