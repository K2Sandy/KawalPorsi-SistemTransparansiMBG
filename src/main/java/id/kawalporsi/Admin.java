package id.kawalporsi;

import static id.kawalporsi.Database.*;
import static id.kawalporsi.Pages.*;
import static id.kawalporsi.Web.*;

import java.sql.*;
import java.util.*;

final class Admin {
  final Pages p;

  Admin(Pages p) {
    this.p = p;
  }

  String reason() {
    return field(
        "Alasan perubahan", "<textarea name='reason' maxlength='500' required></textarea>");
  }

  static String check(String name, String label, boolean on) {
    return "<label class='check'>"
        + input(name, "yes", "checkbox", on ? "checked" : "")
        + h(label)
        + "</label>";
  }

  String render() throws SQLException {
    return switch (p.v) {
      case "rules" -> rules();
      case "foods" -> foods();
      case "isb" -> isb();
      case "users" -> users();
      default -> overview();
    };
  }

  String overview() throws SQLException {
    var all = Service.purchases(p.c, p.u);
    var rs = one(p.c, "select * from rules");
    var b = new StringBuilder();
    for (Row r : rows(p.c, "select * from event where purchase is null order by seq desc limit 8"))
      b.append(
          tr(
              h(r.str("stamp").substring(0, 16)),
              h(r.str("actor")),
              h(r.str("action")),
              h(r.str("note"))));
    return title("Ringkasan provinsi", "Kondisi pengawasan belanja Dapur MBG di Provinsi Aceh.")
        + "<div class='metrics'>"
        + kpi(
            "Dapur terdaftar",
            "" + one(p.c, "select count(*) n from kitchen").num("n"),
            "Provinsi Aceh")
        + kpi(
            "Perlu klarifikasi",
            "" + all.stream().filter(r -> r.str("state").equals("WAITING")).count(),
            "Menunggu jawaban")
        + kpi(
            "Klaim ditahan",
            "" + all.stream().filter(r -> open(r.str("state"))).count(),
            "Dalam pemeriksaan")
        + kpi(
            "Auditor aktif",
            ""
                + one(p.c, "select count(*) n from account where role='AUDITOR' and active=true")
                    .num("n"),
            "Akun aktif")
        + "</div><div class='columns'><div>"
        + paper(
            "Perubahan konfigurasi terbaru",
            table("Waktu|Admin|Perubahan|Alasan", b.toString())
                + link("trail", "Buka jejak audit →"))
        + "</div><div>"
        + paper(
            "Pemantauan otomatis",
            "<p>Ambang aktif <b>±"
                + rs.dec("tolerance").stripTrailingZeros().toPlainString()
                + "%</b></p><p>Strategi utama <b>"
                + (rs.str("strategy").equals("PORTION")
                    ? "Rasio biaya per porsi"
                    : "Deviasi harga satuan")
                + "</b></p>"
                + linkBtn("rules", "Pengaturan audit"))
        + "</div></div>"
        + paper("Transaksi terbaru", p.list(all.stream().limit(8).toList(), false));
  }

  String rules() throws SQLException {
    var r = one(p.c, "select * from rules");
    String f =
        p.form("rules", false)
            + paper(
                "①  Strategi deteksi",
                field(
                        "Strategi utama",
                        "<select name='strategy'>"
                            + option("PORTION", "Rasio biaya per porsi", r.str("strategy"))
                            + option("UNIT", "Deviasi harga satuan", r.str("strategy"))
                            + "</select>")
                    + check(
                        "secondary",
                        "Jalankan strategi lain sebagai pemeriksaan kedua",
                        r.yes("secondary")))
            + paper(
                "②  Batas ambang deviasi",
                field(
                        "Ambang (%)",
                        input(
                            "tolerance",
                            r.dec("tolerance").stripTrailingZeros().toPlainString(),
                            "number",
                            "min='1' max='20' required"))
                    + check("low", "Tandai juga biaya di bawah acuan", r.yes("low_flag")))
            + paper(
                "③  Saat transaksi ditandai",
                check(
                        "notify",
                        "Kirim notifikasi dalam aplikasi ke auditor wilayah",
                        r.yes("notify"))
                    + "<p>Klaim dibekukan, jejak audit dicatat, dan angka transaksi ditahan di"
                    + " dasbor publik.</p>"
                    + reason()
                    + button("Simpan pengaturan"))
            + "</form>";
    return title(
            "Pengaturan audit",
            "Perubahan berlaku untuk transaksi baru dan tercatat di jejak audit.")
        + f;
  }

  String foods() throws SQLException {
    Row current = new Row();
    var b = new StringBuilder();
    for (Row r : rows(p.c, "select * from food order by name")) {
      if (r.str("id").equals(p.q.get("id"))) current = r;
      if ((r.str("id") + " " + r.str("name"))
          .toLowerCase(ID)
          .contains(p.q.get("search").toLowerCase(ID)))
        b.append(
            tr(
                h(r.str("name")) + "<small>" + h(r.str("id")) + "</small>",
                h(r.str("kind")),
                rp(r.num("price")),
                h(BahanPangan.from(r).penyimpanan()),
                link("foods&id=" + url(r.str("id")), "Ubah")));
    }
    String form =
        p.form("price", false)
            + hidden("id", current.str("id"))
            + field(
                "Nama bahan",
                input("name", current.str("name"), "text", "required maxlength='120'"))
            + field(
                "Jenis bahan",
                "<select name='kind'>"
                    + option("Basah", "Basah", current.str("kind"))
                    + option("Kering", "Kering", current.str("kind"))
                    + "</select>")
            + field(
                "Harga acuan per kg (Rp)",
                input("price", current.str("price"), "number", "required min='1'"))
            + field(
                "Masa simpan bahan basah (hari)",
                input("shelf", current.str("shelf_days"), "number", "min=1 max=730"))
            + field(
                "Kedaluwarsa bahan kering (opsional)",
                input("expiry", current.str("expiry"), "date", ""))
            + reason()
            + button("Simpan bahan")
            + "</form>";
    return title(
            "Bahan & harga acuan", "Harga acuan per kg dipakai untuk menilai setiap belanja dapur.")
        + "<div class='columns'><div>"
        + paper(
            "Bahan pangan",
            p.filter("foods", "Cari bahan atau kode")
                + table("Bahan|Jenis|Harga acuan/kg|Penyimpanan|Tindakan", b.toString())
                + link("foods", "＋ Tambah bahan"))
        + "</div><div>"
        + paper(current.isEmpty() ? "Tambah bahan" : "Ubah harga acuan", form)
        + "</div></div>";
  }

  String isb() throws SQLException {
    var regions = rows(p.c, "select * from region order by name");
    var b = new StringBuilder();
    Row edit = regions.getFirst();
    for (Row r : regions) {
      String region = r.str("name");
      if (region.equals(p.q.get("id"))) edit = r;
      b.append(tr(h(region), rp(r.num("isb")), link("isb&id=" + url(region), "Ubah ISB")));
    }
    String form =
        p.form("isb", false)
            + hidden("region", edit.str("name"))
            + "<p><b>"
            + h(edit.str("name"))
            + "</b></p>"
            + field(
                "ISB per porsi (Rp)", input("isb", edit.str("isb"), "number", "min='1' required"))
            + reason()
            + button("Simpan ISB")
            + "</form>";
    return title(
            "ISB daerah", "Tetapkan biaya acuan per porsi untuk transaksi baru di setiap daerah.")
        + "<div class='columns'><div>"
        + paper(
            "Indeks Standar Biaya", table("Kabupaten / kota|ISB per porsi|Tindakan", b.toString()))
        + "</div><div>"
        + paper("Ubah ISB", form)
        + "</div></div>";
  }
}
