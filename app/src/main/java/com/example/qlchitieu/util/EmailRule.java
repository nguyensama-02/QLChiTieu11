package com.example.qlchitieu.util;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Quy tắc email khi đăng ký. Chỉ dùng regex (không dùng android.util.Patterns)
 * nên chạy được trong unit test thường.
 */
public final class EmailRule {

    // Phần trước @ dài 6-30 ký tự, chỉ gồm chữ thường, số và dấu chấm;
    // không bắt đầu hoặc kết thúc bằng dấu chấm, không có hai dấu chấm liền nhau.
    private static final Pattern GMAIL = Pattern.compile(
            "^(?!.*\\.\\.)[a-z0-9][a-z0-9.]{4,28}[a-z0-9]@gmail\\.com$");

    private EmailRule() {
    }

    /** Đưa email về dạng chuẩn: bỏ khoảng trắng hai đầu và viết thường. */
    public static String chuanHoa(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Email (đã chuẩn hóa) có đúng định dạng Gmail không. */
    public static boolean laGmailHopLe(String email) {
        return email != null && GMAIL.matcher(email).matches();
    }
}
