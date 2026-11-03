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
}
