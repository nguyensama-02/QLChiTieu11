package com.example.qlchitieu.model;

public class Budget {

    private String maNganSach;
    private String danhMuc;

    private long soTienGioiHan;
    private long soTienDaChi;

    private int thang;
    private int nam;

    public Budget() {
    }

    public Budget(String maNganSach, String danhMuc,
                  long soTienGioiHan, long soTienDaChi,
                  int thang, int nam) {

        this.maNganSach = maNganSach;
        this.danhMuc = danhMuc;
        this.soTienGioiHan = soTienGioiHan;
        this.soTienDaChi = soTienDaChi;
        this.thang = thang;
        this.nam = nam;
    }

    public String getMaNganSach() {
        return maNganSach;
    }

    public void setMaNganSach(String maNganSach) {
        this.maNganSach = maNganSach;
    }

    public String getDanhMuc() {
        return danhMuc;
    }

    public void setDanhMuc(String danhMuc) {
        this.danhMuc = danhMuc;
    }

    public long getSoTienGioiHan() {
        return soTienGioiHan;
    }

    public void setSoTienGioiHan(long soTienGioiHan) {
        this.soTienGioiHan = soTienGioiHan;
    }

    public long getSoTienDaChi() {
        return soTienDaChi;
    }

    public void setSoTienDaChi(long soTienDaChi) {
        this.soTienDaChi = soTienDaChi;
    }

    public int getThang() {
        return thang;
    }

    public void setThang(int thang) {
        this.thang = thang;
    }

    public int getNam() {
        return nam;
    }

    public void setNam(int nam) {
        this.nam = nam;
    }

    public double getPhanTramSuDung() {

        if (soTienGioiHan <= 0) {
            return 0;
        }

        return (double) soTienDaChi * 100 / soTienGioiHan;
    }
}
