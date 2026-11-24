package com.example.qlchitieu.auth;

import androidx.annotation.StringRes;

import com.example.qlchitieu.R;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;

/**
 * Đổi lỗi của Firebase Auth thành chuỗi tiếng Việt để hiện cho người dùng,
 * thay vì hiện nguyên câu tiếng Anh của Firebase.
 *
 * Lỗi không nhận ra (thường do mạng chập chờn) được báo kèm gợi ý kiểm tra kết nối,
 * để không bị hiểu nhầm thành "sai mật khẩu".
 */
final class AuthErrorMapper {

    private AuthErrorMapper() {
    }

    @StringRes
    static int choDangNhap(Exception e) {
        if (e instanceof FirebaseNetworkException) {
            return R.string.error_network;
        }
        if (e instanceof FirebaseTooManyRequestsException) {
            return R.string.error_too_many;
        }
        if (e instanceof FirebaseAuthInvalidUserException) {
            String ma = ((FirebaseAuthException) e).getErrorCode();
            return "ERROR_USER_DISABLED".equals(ma)
                    ? R.string.login_disabled
                    : R.string.login_failed;      // không có tài khoản: không nói rõ để tránh dò email
        }
        if (e instanceof FirebaseAuthInvalidCredentialsException) {
            return R.string.login_failed;
        }
        return R.string.login_error_generic;
    }

    @StringRes
    static int choDangKy(Exception e) {
        if (e instanceof FirebaseNetworkException) {
            return R.string.error_network;
        }
        if (e instanceof FirebaseTooManyRequestsException) {
            return R.string.error_too_many;
        }
        if (e instanceof FirebaseAuthUserCollisionException) {
            return R.string.register_email_exists;
        }
        if (e instanceof FirebaseAuthWeakPasswordException) {
            return R.string.register_password_weak;
        }
        if (e instanceof FirebaseAuthInvalidCredentialsException) {
            return R.string.register_email_invalid;
        }
        return R.string.register_error_default;
    }

    @StringRes
    static int choQuenMatKhau(Exception e) {
        if (e instanceof FirebaseNetworkException) {
            return R.string.error_network;
        }
        if (e instanceof FirebaseTooManyRequestsException) {
            return R.string.error_too_many;
        }
        return R.string.reset_failed;
    }
}
