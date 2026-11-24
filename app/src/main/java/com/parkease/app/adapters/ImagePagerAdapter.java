package com.parkease.app.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.parkease.app.R;
import com.parkease.app.databinding.ItemGalleryImageBinding;

import java.util.ArrayList;
import java.util.List;

/** Swipeable photo gallery (ViewPager2) for the location details screen. Glide loads each URL. */
public class ImagePagerAdapter extends RecyclerView.Adapter<ImagePagerAdapter.Holder> {

    private final List<String> urls = new ArrayList<>();

    public void setImages(@NonNull List<String> images) {
        if (images.equals(urls)) return;          // same photos → nothing to redraw
        urls.clear();
        urls.addAll(images);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemGalleryImageBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        h.b.imgPhoto.setContentDescription(h.b.imgPhoto.getContext()
                .getString(R.string.cd_location_photo, position + 1, urls.size()));
        Glide.with(h.b.imgPhoto)
                .load(urls.get(position))
                .centerCrop()
                .error(R.drawable.bg_location_placeholder)   // broken link → navy background
                .into(h.b.imgPhoto);
    }

    @Override
    public int getItemCount() {
        return urls.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemGalleryImageBinding b;

        Holder(@NonNull ItemGalleryImageBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }
    }
}
