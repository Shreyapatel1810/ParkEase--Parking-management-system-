package com.parkease.app.ui.common;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.transition.TransitionManager;
import android.view.View;
import android.widget.LinearLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.SystemBarStyle;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.parkease.app.R;
import com.parkease.app.adapters.OnboardingAdapter;
import com.parkease.app.databinding.ActivityOnboardingBinding;
import com.parkease.app.models.OnboardingPage;
import com.parkease.app.utils.SessionManager;

import java.util.Arrays;
import java.util.List;

/**
 * STEP 4 – Onboarding: three swipeable introduction pages.
 * <ul>
 *   <li>Next        → goes to the next page.</li>
 *   <li>Skip        → finishes onboarding immediately.</li>
 *   <li>Get Started → shown on the last page; finishes onboarding.</li>
 *   <li>Back        → previous page (or leaves the app on page 1).</li>
 * </ul>
 * Finishing saves "onboarding completed" in SharedPreferences (SessionManager),
 * so this screen never appears again on later launches.
 */
public class OnboardingActivity extends BaseActivity {

    private ActivityOnboardingBinding binding;
    private List<OnboardingPage> pages;
    private View[] dots;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarsPadding(binding.root);

        pages = Arrays.asList(
                new OnboardingPage(R.drawable.ill_onboarding_find,
                        R.string.onboarding_title_1, R.string.onboarding_desc_1),
                new OnboardingPage(R.drawable.ill_onboarding_book,
                        R.string.onboarding_title_2, R.string.onboarding_desc_2),
                new OnboardingPage(R.drawable.ill_onboarding_park,
                        R.string.onboarding_title_3, R.string.onboarding_desc_3));

        setupPager();
        setupIndicators();
        setupButtons();
        setupBackButton();
        updateForPage(0);
    }

    /** Light screen → dark status bar icons in light mode (white in dark mode). */
    @NonNull
    @Override
    protected SystemBarStyle statusBarStyle() {
        return SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT);
    }

    // ------------------------------------------------------------------ pages

    private void setupPager() {
        binding.viewPager.setAdapter(new OnboardingAdapter(pages));
        binding.viewPager.setOffscreenPageLimit(1);

        // Smooth transition: pages fade and the illustration moves a little slower (parallax)
        binding.viewPager.setPageTransformer((page, position) -> {
            float abs = Math.abs(position);
            page.setAlpha(1f - Math.min(abs, 1f) * 0.6f);
            View illustration = page.findViewById(R.id.imgIllustration);
            if (illustration != null) {
                illustration.setTranslationX(-position * page.getWidth() * 0.25f);
                float scale = 1f - Math.min(abs, 1f) * 0.15f;
                illustration.setScaleX(scale);
                illustration.setScaleY(scale);
            }
        });

        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateForPage(position);
            }
        });
    }

    // ------------------------------------------------------------------ dots

    private void setupIndicators() {
        int size = getResources().getDimensionPixelSize(R.dimen.indicator_dot);
        int gap = getResources().getDimensionPixelSize(R.dimen.space_xs);
        dots = new View[pages.size()];
        for (int i = 0; i < dots.length; i++) {
            View dot = new View(this);
            dot.setBackgroundResource(R.drawable.bg_page_indicator);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMarginStart(gap);
            params.setMarginEnd(gap);
            binding.indicatorContainer.addView(dot, params);
            dots[i] = dot;

            final int page = i;
            dot.setOnClickListener(v -> binding.viewPager.setCurrentItem(page));
        }
    }

    /** Active dot becomes a wide teal pill; the others are small grey dots. */
    private void updateIndicators(int selected) {
        TransitionManager.beginDelayedTransition(binding.indicatorContainer);
        int small = getResources().getDimensionPixelSize(R.dimen.indicator_dot);
        int wide = getResources().getDimensionPixelSize(R.dimen.indicator_active);
        ColorStateList active = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brand_secondary));
        ColorStateList inactive = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.stroke));
        for (int i = 0; i < dots.length; i++) {
            boolean on = i == selected;
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) dots[i].getLayoutParams();
            params.width = on ? wide : small;
            dots[i].setLayoutParams(params);
            dots[i].setBackgroundTintList(on ? active : inactive);
        }
    }

    // ------------------------------------------------------------------ buttons

    private void setupButtons() {
        binding.btnNext.setOnClickListener(v -> {
            int current = binding.viewPager.getCurrentItem();
            if (isLastPage(current)) {
                finishOnboarding();
            } else {
                binding.viewPager.setCurrentItem(current + 1, true);
            }
        });
        binding.btnSkip.setOnClickListener(v -> finishOnboarding());
    }

    /** Updates dots, button text and Skip visibility for the visible page. */
    private void updateForPage(int position) {
        updateIndicators(position);
        boolean last = isLastPage(position);

        binding.btnNext.setText(last ? R.string.onboarding_get_started : R.string.onboarding_next);
        binding.btnNext.setIconResource(last ? 0 : R.drawable.ic_chevron_right);

        // Skip is pointless on the last page
        binding.btnSkip.animate().alpha(last ? 0f : 1f).setDuration(200)
                .withEndAction(() -> binding.btnSkip.setVisibility(last ? View.INVISIBLE : View.VISIBLE))
                .start();
        if (!last) binding.btnSkip.setVisibility(View.VISIBLE);

        binding.indicatorContainer.setContentDescription(
                getString(R.string.cd_onboarding_page, position + 1, pages.size()));
    }

    private boolean isLastPage(int position) {
        return position == pages.size() - 1;
    }

    /** Saves the flag (so onboarding is shown only once) and opens the next screen. */
    private void finishOnboarding() {
        SessionManager.setOnboardingCompleted(this, true);
        AppNavigator.openEntryScreen(this);
    }

    // ------------------------------------------------------------------ back button

    private void setupBackButton() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                int current = binding.viewPager.getCurrentItem();
                if (current > 0) {
                    binding.viewPager.setCurrentItem(current - 1, true);
                } else {
                    finish(); // leave the app; onboarding shows again next time
                }
            }
        });
    }
}
