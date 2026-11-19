package com.example.qlchitieu.util;

import java.util.Calendar;

/**
 * Đổi (tháng, năm) thành khoảng thời gian [đầu tháng, đầu tháng sau) tính bằng mili-giây,
 * dùng để lọc cột ngày (epoch ms) trong SQLite.
 */
public final class MonthRange {

    private MonthRange() {
    }

    /** 00:00:00.000 ngày 1 của tháng (thang: 1..12). */
    public static long start(int thang, int nam) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(nam, thang - 1, 1, 0, 0, 0);
        return c.getTimeInMillis();
    }

    /** 00:00:00.000 ngày 1 của tháng kế tiếp (không bao gồm). */
    public static long end(int thang, int nam) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(nam, thang - 1, 1, 0, 0, 0);
        c.add(Calendar.MONTH, 1);
        return c.getTimeInMillis();
    }
}
