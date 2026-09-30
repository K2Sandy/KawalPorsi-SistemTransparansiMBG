package id.kawalporsi;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Reference item as specified in the proposal's class diagram. */
public abstract sealed class BahanPangan permits BahanBasah, BahanKering {
  private final String id;
  private final String nama;
  private final long hargaAcuanPerKg;

  BahanPangan(String id, String nama, long harga) {
    this.id = id;
    this.nama = nama;
    this.hargaAcuanPerKg = harga;
  }

  public String id() {
    return id;
  }

  public String nama() {
    return nama;
  }

  public long hargaAcuanPerKg() {
    return hargaAcuanPerKg;
  }

  public BigDecimal hitungTotalAcuan(BigDecimal volume) {
    return volume.multiply(BigDecimal.valueOf(hargaAcuanPerKg));
  }

  public abstract String penyimpanan();

  static BahanPangan from(Database.Row r) {
    return r.str("kind").equals("Basah")
        ? new BahanBasah(
            r.str("id"),
            r.str("name"),
            r.num("price"),
            r.get("shelf_days") == null ? null : (int) r.num("shelf_days"))
        : new BahanKering(
            r.str("id"),
            r.str("name"),
            r.num("price"),
            r.get("expiry") == null ? null : LocalDate.parse(r.str("expiry")));
  }
}

final class BahanBasah extends BahanPangan {
  private final Integer masaSimpanHari;

  BahanBasah(String id, String nama, long harga, Integer hari) {
    super(id, nama, harga);
    masaSimpanHari = hari;
  }

  public String penyimpanan() {
    return masaSimpanHari == null ? "Belum dicatat" : masaSimpanHari + " hari";
  }
}

final class BahanKering extends BahanPangan {
  private final LocalDate kedaluwarsa;

  BahanKering(String id, String nama, long harga, LocalDate batas) {
    super(id, nama, harga);
    kedaluwarsa = batas;
  }

  public String penyimpanan() {
    return kedaluwarsa == null ? "Periksa tanggal pada kemasan" : "Kedaluwarsa " + kedaluwarsa;
  }
}
