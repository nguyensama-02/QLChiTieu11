package com.example.qlchitieu.util;

import android.app.Activity;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.qlchitieu.R;

/**
 * App chạy edge-to-edge (targetSdk 35+), nên header gradient bị đè dưới status bar.
 * Hàm này cộng thêm chiều cao status bar vào padding trên của header (id layoutHeader)
 * và chừa chỗ cho thanh điều hướng ở dưới cùng.
 */
public final class InsetsUtil {

    private InsetsUtil() {
    }

    public static void apply(Activity activity) {

        View content = activity.findViewById(android.R.id.content);
        View header = activity.findViewById(R.id.layoutHeader);

        if (content == null) {
            return;
        }

        final int baseTop = header == null ? 0 : header.getPaddingTop();

        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {

            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );

            if (header != null) {
                header.setPadding(
                        header.getPaddingLeft(),
                        baseTop + bars.top,
                        header.getPaddingRight(),
                        header.getPaddingBottom()
                );
            }

            v.setPadding(bars.left, 0, bars.right, bars.bottom);

            return insets;
        });

        ViewCompat.requestApplyInsets(content);
    }
}
