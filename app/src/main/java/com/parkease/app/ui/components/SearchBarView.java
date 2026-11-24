package com.parkease.app.ui.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.card.MaterialCardView;
import com.parkease.app.R;
import com.parkease.app.databinding.ViewSearchBarBinding;

/**
 * Rounded search field with a search icon, clear (x) button and optional filter button.
 *
 * XML:
 * <pre>
 * &lt;com.parkease.app.ui.components.SearchBarView
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:sbHint="@string/..."
 *     app:sbShowFilter="true" /&gt;
 * </pre>
 * Java:
 * <pre>
 * searchBar.setOnSearchListener(query -> search(query));        // keyboard "Search" key
 * searchBar.setOnQueryChangeListener(query -> filterList(query)); // every letter typed
 * searchBar.setOnFilterClickListener(v -> openFilters());
 * </pre>
 */
public class SearchBarView extends MaterialCardView {

    public interface OnSearchListener {
        void onSearch(@NonNull String query);
    }

    public interface OnQueryChangeListener {
        void onQueryChanged(@NonNull String query);
    }

    private final ViewSearchBarBinding binding;
    private OnSearchListener searchListener;
    private OnQueryChangeListener queryChangeListener;

    public SearchBarView(@NonNull Context context) {
        this(context, null);
    }

    public SearchBarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, com.google.android.material.R.attr.materialCardViewStyle);
    }

    public SearchBarView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        binding = ViewSearchBarBinding.inflate(LayoutInflater.from(context), this);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SearchBarView);
        try {
            binding.etSearch.setHint(a.getString(R.styleable.SearchBarView_sbHint));
            setFilterVisible(a.getBoolean(R.styleable.SearchBarView_sbShowFilter, true));
        } finally {
            a.recycle();
        }

        // Show the clear button only when there is text, and report changes
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                binding.btnClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                if (queryChangeListener != null) {
                    queryChangeListener.onQueryChanged(s.toString().trim());
                }
            }
        });

        // Keyboard "Search" key
        binding.etSearch.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enter) {
                hideKeyboard();
                if (searchListener != null) {
                    searchListener.onSearch(getQuery());
                }
                return true;
            }
            return false;
        });

        binding.btnClear.setOnClickListener(v -> binding.etSearch.setText(""));

        // Tapping anywhere on the bar focuses the text field
        setOnClickListener(v -> {
            binding.etSearch.requestFocus();
            InputMethodManager imm = (InputMethodManager)
                    getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    @NonNull
    public String getQuery() {
        return binding.etSearch.getText() == null ? "" : binding.etSearch.getText().toString().trim();
    }

    public void setQuery(@Nullable String query) {
        binding.etSearch.setText(query);
    }

    public void setHint(@Nullable CharSequence hint) {
        binding.etSearch.setHint(hint);
    }

    public void setFilterVisible(boolean visible) {
        int v = visible ? View.VISIBLE : View.GONE;
        binding.btnFilter.setVisibility(v);
        binding.filterDivider.setVisibility(v);
    }

    public void setOnSearchListener(@Nullable OnSearchListener listener) {
        this.searchListener = listener;
    }

    public void setOnQueryChangeListener(@Nullable OnQueryChangeListener listener) {
        this.queryChangeListener = listener;
    }

    public void setOnFilterClickListener(@Nullable View.OnClickListener listener) {
        binding.btnFilter.setOnClickListener(listener);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager)
                getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(binding.etSearch.getWindowToken(), 0);
        binding.etSearch.clearFocus();
    }
}
