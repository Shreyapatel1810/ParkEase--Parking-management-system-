package com.parkease.app.models;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

/** Content of one onboarding page: illustration, title and description. */
public class OnboardingPage {

    @DrawableRes
    private final int illustration;
    @StringRes
    private final int title;
    @StringRes
    private final int description;

    public OnboardingPage(@DrawableRes int illustration, @StringRes int title, @StringRes int description) {
        this.illustration = illustration;
        this.title = title;
        this.description = description;
    }

    @DrawableRes
    public int getIllustration() {
        return illustration;
    }

    @StringRes
    public int getTitle() {
        return title;
    }

    @StringRes
    public int getDescription() {
        return description;
    }
}
