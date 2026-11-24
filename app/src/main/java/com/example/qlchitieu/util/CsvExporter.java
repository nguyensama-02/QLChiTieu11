package com.example.qlchitieu.util;

import com.example.qlchitieu.model.Transaction;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Xuất giao dịch ra file CSV.
 *
 * - UTF-8 có BOM để Excel hiển thị đúng tiếng Việt.
 * - Dòng đầu "sep=," cho Excel biết dấu phân cách, nên mở được trên cả máy cài
 *   vùng Việt Nam (dấu phân cách mặc định là ";") lẫn máy cài vùng khác.
 *   (Google Sheets sẽ hiện dòng này như một dòng dữ liệu, có thể xóa đi.)
 * - Chống "CSV injection": ô bắt đầu bằng = + - @ được thêm dấu ' phía trước
 *   để Excel không hiểu nhầm là công thức.
 */
public final class CsvExporter {

    private static final String XUONG_DONG = "\r\n";

    private CsvExporter() {
    }

    /**
     * Ghi giao dịch theo thứ tự thời gian tăng dần.
     *
     * @param danhSach giao dịch (thứ tự bất kỳ, hàm tự đảo nếu danh sách đang mới nhất trước)
     * @param tenVi    bản đồ mã ví -> tên ví, dùng để ghi tên thay vì mã
     */
    public static void xuatGiaoDich(OutputStream out,
                                    List<Transaction> danhSach,
                                    Map<String, String> tenVi) throws IOException {

        SimpleDateFormat dinhDangNgay =
                new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("vi", "VN"));

        Writer w = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));

        w.write('\uFEFF');
        w.write("sep=," + XUONG_DONG);
        w.write("Ngày giao dịch,Loại,Danh mục,Ví,Ví nhận,Số tiền (VNĐ),Ghi chú" + XUONG_DONG);

        // Cũ nhất lên trên
        for (int i = danhSach.size() - 1; i >= 0; i--) {
            Transaction t = danhSach.get(i);

            w.write(o(dinhDangNgay.format(t.getNgayGiaoDich())));
            w.write(',');
            w.write(o(t.getLoaiGiaoDich()));
            w.write(',');
            w.write(o(t.getDanhMuc()));
            w.write(',');
            w.write(o(tenViCuaMa(tenVi, t.getMaViNguon())));
            w.write(',');
            w.write(o(t.getMaViDich() == null ? "" : tenViCuaMa(tenVi, t.getMaViDich())));
            w.write(',');
            w.write(String.valueOf(t.getSoTien()));
            w.write(',');
            w.write(o(t.getGhiChu()));
            w.write(XUONG_DONG);
        }

        w.flush();
    }

    private static String tenViCuaMa(Map<String, String> tenVi, String maVi) {
        if (maVi == null) {
            return "";
        }

        String ten = tenVi.get(maVi);

        return ten == null ? "(ví đã xóa)" : ten;
    }

    /** Chuẩn hóa một ô theo quy tắc CSV (RFC 4180) và chặn công thức độc hại. */
    private static String o(String giaTri) {
        if (giaTri == null) {
            return "";
        }

        String s = giaTri;

        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }

        boolean canNgoacKep = s.indexOf(',') >= 0
                || s.indexOf('"') >= 0
                || s.indexOf('\n') >= 0
                || s.indexOf('\r') >= 0;

        if (!canNgoacKep) {
            return s;
        }

        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}