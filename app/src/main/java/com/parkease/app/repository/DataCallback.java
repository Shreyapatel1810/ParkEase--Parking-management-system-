package com.parkease.app.repository;

import androidx.annotation.NonNull;

/**
 * How a repository returns a result to a screen.
 * Firebase works in the background, so results arrive later through this callback
 * (always on the main thread, so it is safe to update views).
 *
 * <pre>
 * authRepository.login(email, password, new DataCallback&lt;User&gt;() {
 *     public void onSuccess(User user) { ... open dashboard ... }
 *     public void onError(String message) { ... show message ... }
 * });
 * </pre>
 */
public interface DataCallback<T> {

    void onSuccess(T data);

    /** @param message a user-friendly message, ready to show on screen */
    void onError(@NonNull String message);
}
