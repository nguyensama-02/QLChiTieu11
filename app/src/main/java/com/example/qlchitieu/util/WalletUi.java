package com.example.qlchitieu.util;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;

import com.example.qlchitieu.R;

/** Icon + màu theo loại ví, dùng chung cho danh sách ví và chi tiết ví. */
public final class WalletUi {

    private WalletUi() {
    }

    public static int icon(String loaiVi) {
        if ("Tiền mặt".equals(loaiVi)) {
            return R.drawable.ic_cash;
        }
        if ("Ngân hàng".equals(loaiVi)) {
            return R.drawable.ic_bank;
        }
        return R.drawable.ic_ewallet;
    }

    public static int mauChinh(Context c, String loaiVi) {
        if ("Tiền mặt".equals(loaiVi)) {
            return ContextCompat.getColor(c, R.color.wallet_cash);
        }
        if ("Ngân hàng".equals(loaiVi)) {
            return ContextCompat.getColor(c, R.color.wallet_bank);
        }
        return ContextCompat.getColor(c, R.color.wallet_ewallet);
    }

    public static int mauNen(Context c, String loaiVi) {
        if ("Tiền mặt".equals(loaiVi)) {
            return ContextCompat.getColor(c, R.color.wallet_cash_bg);
        }
        if ("Ngân hàng".equals(loaiVi)) {
            return ContextCompat.getColor(c, R.color.wallet_bank_bg);
        }
        return ContextCompat.getColor(c, R.color.wallet_ewallet_bg);
    }

    /** Gán icon + màu nền bo góc cho ô icon ví. */
    public static void gan(ImageView iv, String loaiVi) {
        Context c = iv.getContext();

        iv.setImageResource(icon(loaiVi));
        iv.setImageTintList(ColorStateList.valueOf(mauChinh(c, loaiVi)));

        GradientDrawable nen = new GradientDrawable();
        nen.setCornerRadius(16f * c.getResources().getDisplayMetrics().density);
        nen.setColor(mauNen(c, loaiVi));
        iv.setBackground(nen);
    }
}
