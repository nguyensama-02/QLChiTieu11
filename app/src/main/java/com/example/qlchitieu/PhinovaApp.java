package com.example.qlchitieu;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;

import com.example.qlchitieu.auth.LoginActivity;
import com.example.qlchitieu.auth.RegisterActivity;
import com.example.qlchitieu.security.AppLock;
import com.example.qlchitieu.security.LockActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Theo dõi vòng đời các màn hình để áp dụng "Khóa ứng dụng":
 * khi app quay lại từ nền (quá hạn) hoặc mới mở, nếu tài khoản bật khóa thì hiện màn hình khóa.
 */
public class PhinovaApp extends Application {

    private int soManHinhDangHien = 0;

    /** Đang xoay màn hình: không tính là thoát app rồi quay lại. */
    private boolean dangDoiCauHinh = false;

    @Override
    public void onCreate() {
        super.onCreate();

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {

            @Override
            public void onActivityStarted(Activity activity) {

                if (soManHinhDangHien == 0 && !dangDoiCauHinh) {
                    AppLock.onVaoLai(PhinovaApp.this);
                }

                dangDoiCauHinh = false;
                soManHinhDangHien++;

                kiemTraKhoa(activity);
            }

            @Override
            public void onActivityStopped(Activity activity) {

                soManHinhDangHien = Math.max(0, soManHinhDangHien - 1);

                if (activity.isChangingConfigurations()) {
                    dangDoiCauHinh = true;
                    return;
                }

                if (soManHinhDangHien == 0) {
                    AppLock.onRaNen();
                }
            }

            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            }

            @Override
            public void onActivityResumed(Activity activity) {
            }

            @Override
            public void onActivityPaused(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
            }
        });
    }

    private void kiemTraKhoa(Activity activity) {

        // Màn khóa và các màn đăng nhập / đăng ký không bị chặn
        if (activity instanceof LockActivity
                || activity instanceof LoginActivity
                || activity instanceof RegisterActivity) {
            return;
        }

        FirebaseUser nguoiDung = FirebaseAuth.getInstance().getCurrentUser();

        if (nguoiDung == null || !AppLock.canMoKhoa(this, nguoiDung.getUid())) {
            return;
        }

        if (AppLock.isManKhoaDangMo()) {
            return;
        }

        AppLock.setManKhoaDangMo(true);
        activity.startActivity(new Intent(activity, LockActivity.class));
    }
}
