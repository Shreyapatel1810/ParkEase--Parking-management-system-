package com.parkease.app.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.parkease.app.databinding.ItemOnboardingPageBinding;
import com.parkease.app.models.OnboardingPage;

import java.util.List;

/** Supplies the onboarding pages to the ViewPager2 (one item = one full page). */
public class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.PageViewHolder> {

    private final List<OnboardingPage> pages;

    public OnboardingAdapter(@NonNull List<OnboardingPage> pages) {
        this.pages = pages;
    }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemOnboardingPageBinding binding = ItemOnboardingPageBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new PageViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        holder.bind(pages.get(position));
    }

    @Override
    public int getItemCount() {
        return pages.size();
    }

    static class PageViewHolder extends RecyclerView.ViewHolder {

        private final ItemOnboardingPageBinding binding;

        PageViewHolder(@NonNull ItemOnboardingPageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull OnboardingPage page) {
            binding.imgIllustration.setImageResource(page.getIllustration());
            binding.tvTitle.setText(page.getTitle());
            binding.tvDescription.setText(page.getDescription());
        }
    }
}
