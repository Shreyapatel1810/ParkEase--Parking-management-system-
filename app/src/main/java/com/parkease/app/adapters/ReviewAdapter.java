package com.parkease.app.adapters;

import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.parkease.app.R;
import com.parkease.app.databinding.ItemReviewBinding;
import com.parkease.app.models.Review;

import java.util.Objects;

/** Customer reviews list on the location details screen. */
public class ReviewAdapter extends ListAdapter<Review, ReviewAdapter.Holder> {

    public ReviewAdapter() {
        super(DIFF);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemReviewBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Review r = getItem(position);
        h.b.tvInitials.setText(r.getInitials());
        h.b.tvName.setText(r.getUserName());
        h.b.tvDate.setText(r.getCreatedAt() > 0 ? DateUtils.getRelativeTimeSpanString(r.getCreatedAt()) : "");
        h.b.ratingBar.setRating(r.getRating());
        h.b.ratingBar.setContentDescription(h.b.ratingBar.getContext()
                .getString(R.string.cd_rating, String.valueOf(r.getRating())));
        boolean hasComment = !TextUtils.isEmpty(r.getComment());
        h.b.tvComment.setVisibility(hasComment ? View.VISIBLE : View.GONE);
        h.b.tvComment.setText(r.getComment());
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemReviewBinding b;

        Holder(@NonNull ItemReviewBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }
    }

    private static final DiffUtil.ItemCallback<Review> DIFF = new DiffUtil.ItemCallback<Review>() {
        @Override
        public boolean areItemsTheSame(@NonNull Review a, @NonNull Review b) {
            return Objects.equals(a.getReviewerId(), b.getReviewerId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Review a, @NonNull Review b) {
            return a.getRating() == b.getRating() && a.getCreatedAt() == b.getCreatedAt()
                    && Objects.equals(a.getComment(), b.getComment());
        }
    };
}
