package com.parkease.app.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.parkease.app.models.User;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Profile photo helpers.
 *
 * Why Base64 instead of Firebase Storage?
 * Firebase Storage needs the paid Blaze plan for new projects. To keep ParkEase free,
 * the photo is shrunk to a 320 × 320 JPEG (about 25–40 KB) and saved as text inside the
 * user's Realtime Database record. That is fine for one small avatar per user.
 */
public final class ImageUtils {

    private static final String TAG = "ImageUtils";
    public static final int AVATAR_SIZE_PX = 320;
    private static final int JPEG_QUALITY = 80;

    /** Image work is slow, so it runs on one background thread, never on the UI thread. */
    private static final ExecutorService BACKGROUND = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface EncodeCallback {
        /** @param base64 the encoded photo, or null if the image could not be read */
        void onResult(@Nullable String base64);
    }

    private ImageUtils() { }

    /**
     * Reads the picked image, crops it to a centred square, resizes it to 320 px,
     * compresses it as JPEG and returns it as Base64 text (on the main thread).
     */
    public static void encodeProfilePhoto(@NonNull Context context, @NonNull Uri uri,
                                          @NonNull EncodeCallback callback) {
        Context app = context.getApplicationContext();
        BACKGROUND.execute(() -> {
            String result = null;
            try {
                Bitmap source = decodeScaled(app, uri, AVATAR_SIZE_PX * 2);
                if (source != null) {
                    Bitmap square = centerCropSquare(source, AVATAR_SIZE_PX);
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    square.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
                    result = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
                }
            } catch (Exception | OutOfMemoryError e) {
                Log.w(TAG, "Could not encode photo", e);
            }
            final String encoded = result;
            MAIN.post(() -> callback.onResult(encoded));
        });
    }

    /** Turns stored Base64 text back into a picture. Returns null if the text is damaged. */
    @Nullable
    public static Bitmap decodeBase64(@Nullable String base64) {
        if (base64 == null || base64.isEmpty()) return null;
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Shows the user's photo in {@code image}; if there is no photo, shows their initials
     * in {@code initials} instead. Used on every screen with an avatar.
     */
    public static void showAvatar(@NonNull ImageView image, @NonNull TextView initials,
                                  @Nullable User user) {
        Bitmap photo = user == null ? null : decodeBase64(user.getPhoto());
        if (photo != null) {
            image.setImageBitmap(photo);
            image.setVisibility(View.VISIBLE);
            initials.setVisibility(View.INVISIBLE);
        } else {
            image.setImageDrawable(null);
            image.setVisibility(View.GONE);
            initials.setVisibility(View.VISIBLE);
            initials.setText(user == null ? "?" : user.getInitials());
        }
    }

    // ------------------------------------------------------------------ internals

    /** Decodes the image already reduced to about maxSize, using little memory. */
    @Nullable
    private static Bitmap decodeScaled(@NonNull Context context, @NonNull Uri uri, int maxSize)
            throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder also fixes the rotation of photos taken in portrait mode
            ImageDecoder.Source src = ImageDecoder.createSource(context.getContentResolver(), uri);
            return ImageDecoder.decodeBitmap(src, (decoder, info, s) -> {
                int w = info.getSize().getWidth();
                int h = info.getSize().getHeight();
                float scale = Math.min(1f, (float) maxSize / Math.min(w, h));
                decoder.setTargetSize(Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)));
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
        }
        // Android 8.0–8.1: read the size first, then decode a smaller version
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        int sample = 1;
        while (Math.min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSize) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(in, null, opts);
        }
    }

    @NonNull
    private static Bitmap centerCropSquare(@NonNull Bitmap src, int size) {
        int side = Math.min(src.getWidth(), src.getHeight());
        int x = (src.getWidth() - side) / 2;
        int y = (src.getHeight() - side) / 2;
        Bitmap cropped = Bitmap.createBitmap(src, x, y, side, side);
        return Bitmap.createScaledBitmap(cropped, size, size, true);
    }
}
