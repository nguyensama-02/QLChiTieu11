package com.example.qlchitieu.util;

/**
 * Quy tắc mật khẩu khi đăng ký (không áp dụng khi đăng nhập, để tài khoản cũ
 * có mật khẩu 6 ký tự vẫn vào được).
 *
 * Yêu cầu: từ 8 ký tự trở lên, có ít nhất một chữ cái và một chữ số.
 */
public final class PasswordRule {

    public static final int DO_DAI_TOI_THIEU = 8;

    private PasswordRule() {
    }

    public static boolean laHopLe(String matKhau) {
        if (matKhau == null || matKhau.length() < DO_DAI_TOI_THIEU) {
            return false;
        }

        boolean coChu = false;
        boolean coSo = false;

        for (int i = 0; i < matKhau.length(); i++) {
            char c = matKhau.charAt(i);
            if (Character.isLetter(c)) {
                coChu = true;
            } else if (Character.isDigit(c)) {
                coSo = true;
            }
        }

        return coChu && coSo;
    }
}
