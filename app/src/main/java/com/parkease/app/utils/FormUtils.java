package com.parkease.app.utils;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.google.android.material.textfield.TextInputLayout;

/** Helpers shared by all forms (login, register, vehicles, locations …). */
public final class FormUtils {

    private FormUtils() { }

    /** Text of the field inside a TextInputLayout ("" if empty). */
    @NonNull
    public static String text(@NonNull TextInputLayout layout) {
        EditText et = layout.getEditText();
        return et == null || et.getText() == null ? "" : et.getText().toString();
    }

    /**
     * Shows or clears an error under a field.
     * @return true if the field is VALID (errorRes == 0)
     */
    public static boolean showError(@NonNull TextInputLayout layout, @StringRes int errorRes) {
        if (errorRes == 0) {
            layout.setError(null);
            layout.setErrorEnabled(false);   // removes the empty space under the field
            return true;
        }
        layout.setError(layout.getContext().getString(errorRes));
        return false;
    }

    /** Removes a field's error as soon as the user starts correcting it. */
    public static void clearErrorOnType(@NonNull TextInputLayout... layouts) {
        for (TextInputLayout layout : layouts) {
            EditText et = layout.getEditText();
            if (et == null) continue;
            et.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }

                @Override
                public void afterTextChanged(Editable s) {
                    if (layout.getError() != null) {
                        layout.setError(null);
                        layout.setErrorEnabled(false);
                    }
                }
            });
        }
    }

    /** Puts the cursor in the first field that has an error. */
    public static void focusFirstError(@NonNull TextInputLayout... layouts) {
        for (TextInputLayout layout : layouts) {
            if (layout.getError() != null && layout.getEditText() != null) {
                layout.getEditText().requestFocus();
                return;
            }
        }
    }
}
