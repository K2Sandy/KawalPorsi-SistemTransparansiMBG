package id.kawalporsi;

import static id.kawalporsi.Database.*;
import static id.kawalporsi.Pages.*;
import static id.kawalporsi.Service.PURCHASE;
import static id.kawalporsi.Web.*;

import java.sql.*;
import java.time.*;
import java.util.*;

final class PublicPages {
  final Pages p;

  PublicPages(Pages p) {
    this.p = p;
  }

  List<Row> data() throws SQLException {
    return rows(p.c, PURCHASE + " where t.state<>'DRAFT' order by t.day_date desc,k.name");
  }

  String latest() throws SQLException {
    StringBuilder b = new StringBuilder();
    Set<String> seen = new HashSet<>();
    for (Row r : data()) {
      if (!seen.add(r.str("kitchen"))) continue;
      b.append(
          tr(
              "<b>" + h(r.str("kitchen_name")) + "</b><small>" + h(r.str("region")) + "</small>",
              (released(r.str("state")) ? rp(cost(r)) : "—") + "<small>per porsi</small>",
              badge(statusPublic(r.str("state")))));
      if (seen.size() == 4) break;
    }
    return "<div class='latest'><div class='latestHead'><b>Laporan terbaru di dasbor"
               + " publik</b><small>● Data demo</small></div><table><tbody>"
        + b
        + "</tbody></table></div>";
  }

  String render(String flash) throws SQLException {
    String content =
        switch (p.v) {
          case "method" ->
              title("Cara membaca data", "Panduan membaca biaya dan status belanja dapur.")
                  + method();
          case "report" -> report();
          case "directory" ->
              title("Daftar dapur", "Cari dapur menurut nama atau wilayah.") + directory();
          case "kitchen" -> detail();
          default -> dashboard();
        };
    return "<div class='public'><header class='publicNav'>"
        + a("public", logo(), "")
        + "<nav>"
        + link("public", "Dasbor")
        + link("directory", "Daftar dapur")
        + link("method", "Cara membaca data")
        + "</nav><div>"
        + link("report", "Laporkan kejanggalan")
        + linkBtn("login", "Masuk petugas")
        + "</div></header><main>"
        + flash
        + content
        + "</main><footer>"
        + logo()
        + "<p>Data contoh untuk demonstrasi Kelompok 10. Hanya biaya bahan pangan yang"
        + " dihitung.</p>"
        + link("method", "Metodologi")
        + " · "
        + link("report", "Laporkan kejanggalan")
        + "</footer></div>";
  }

  String report() throws SQLException {
    return title(
            "Form laporan warga", "Sampaikan kejanggalan yang Anda amati kepada auditor wilayah.")
        + paper(
            "Laporkan kejanggalan",
            p.form("report", false)
                + field(
                    "Dapur",
                    "<select name='kitchen' required>"
                        + p.opts("select id,name from kitchen order by name", "")
                        + "</select>")
                + field(
                    "Tanggal kejadian",
                    input(
                        "date",
                        LocalDate.now().toString(),
                        "date",
                        "required max='" + LocalDate.now() + "'"))
                + field(
                    "Uraian kejadian",
                    "<textarea name='note' required maxlength='1200' placeholder='Tuliskan waktu,"
                        + " lokasi, dan hal yang Anda amati.'></textarea>")
                + button("Kirim laporan")
                + "</form>");
  }

  static String method() {
    return paper(
            "Apa yang dihitung?",
            "<p>Biaya per porsi = jumlah belanja bahan pangan dibagi porsi yang dimasak. Biaya"
                + " operasional seperti gas dan gaji tidak dihitung. Rata-rata menghitung total"
                + " rupiah dibagi total porsi pada catatan yang disetujui.</p>")
        + paper(
            "Status pada dasbor",
            badge("APPROVED")
                + "<p>Transaksi lolos uji sesuai aturan yang berlaku saat dikirim.</p>"
                + badge("PENDING")
                + "<p>Auditor memeriksa bukti. Biaya dan deviasi transaksi disembunyikan dan"
                + " dikeluarkan dari rata-rata.</p>"
                + badge("CLEARED")
                + "<p>Auditor menerima penjelasan setelah memeriksa transaksi.</p>"
                + badge("REJECTED")
                + "<p>Auditor menolak klaim. Nilai ini tidak masuk rata-rata biaya yang"
                + " disetujui.</p>");
  }

  static List<Row> releasedOnly(List<Row> data) {
    return data.stream().filter(r -> released(r.str("state"))).toList();
  }

  static double average(List<Row> data) {
    long total = data.stream().mapToLong(r -> r.num("total")).sum(),
        n = data.stream().mapToLong(r -> r.num("portions")).sum();
    return n == 0 ? 0 : total / (double) n;
  }

  List<Row> period(List<Row> all) {
    String m = p.q.get("month").isEmpty() ? YearMonth.now().toString() : p.q.get("month");
    return all.stream()
        .filter(
            r ->
                r.str("day_date").startsWith(m)
                    && (p.q.get("region").isEmpty() || p.q.get("region").equals(r.str("region"))))
        .toList();
  }

  String dashboard() throws SQLException {
    List<Row> set = period(data()), approved = releasedOnly(set);
    String form =
        "<form class='filters' method='get'>"
            + hidden("v", "public")
            + field(
                "Wilayah",
                "<select name='region'>"
                    + option("", "Provinsi Aceh", p.q.get("region"))
                    + p.opts("select name id,name from region order by name", p.q.get("region"))
                    + "</select>")
            + field(
                "Periode",
                input(
                    "month",
                    p.q.get("month").isEmpty() ? YearMonth.now().toString() : p.q.get("month"),
                    "month",
                    "required"))
            + button("Terapkan")
            + "</form>";
    StringBuilder bars = new StringBuilder();
    for (String region : set.stream().map(r -> r.str("region")).distinct().toList()) {
      List<Row> regionData = approved.stream().filter(r -> r.str("region").equals(region)).toList();
      bars.append(
          tr(
              h(region),
              regionData.isEmpty() ? "—" : rp(average(regionData)),
              regionData.isEmpty()
                  ? "—"
                  : gauge(
                      average(regionData),
                      regionData.stream()
                              .mapToDouble(r -> r.num("isb") * (double) r.num("portions"))
                              .sum()
                          / regionData.stream().mapToLong(r -> r.num("portions")).sum(),
                      regionData.getFirst().dec("tolerance").doubleValue())));
    }
    long pending = set.stream().filter(r -> open(r.str("state"))).count();
    return title(
            "Transparansi belanja Dapur MBG",
            "Biaya bahan pangan untuk satu porsi makan bergizi gratis dibandingkan dengan Indeks"
                + " Standar Biaya (ISB) daerah.")
        + form
        + "<div class='metrics'>"
        + kpi(
            "Rata-rata biaya per porsi",
            approved.isEmpty() ? "—" : rp(average(approved)),
            "Transaksi disetujui")
        + kpi(
            "Dapur yang melapor",
            "" + set.stream().map(r -> r.str("kitchen")).distinct().count(),
            "Dalam periode terpilih")
        + kpi(
            "Porsi dilaporkan",
            num(set.stream().mapToLong(r -> r.num("portions")).sum()),
            "Sepanjang periode")
        + kpi("Sedang diverifikasi", "" + pending, "Biaya disembunyikan")
        + "</div>"
        + "<div class='columns'><div>"
        + paper(
            "Biaya bahan per porsi menurut kabupaten/kota",
            table("Wilayah|Rata-rata|Terhadap ISB", bars.toString()))
        + "</div><div>"
        + paper(
            "Status transaksi",
            trSummary(approved.size(), pending, set.size() - approved.size() - pending)
                + link("method", "Pelajari metode penghitungan →"))
        + "</div></div>"
        + trend(approved)
        + directory();
  }

  static String trSummary(long ok, long pending, long rejected) {
    return "<p>"
        + badge("APPROVED")
        + " <b>"
        + ok
        + "</b></p><p>"
        + badge("PENDING")
        + " <b>"
        + pending
        + "</b></p><p>"
        + badge("REJECTED")
        + " <b>"
        + rejected
        + "</b></p>";
  }

  static String gauge(double value, double base, double tolerance) {
    double pos = Math.max(0, Math.min(100, 50 + (value / base - 1) * 200)),
        half = Math.min(50, tolerance * 2);
    return "<div class='gauge' title='"
        + pct((value / base - 1) * 100)
        + "'><i style='left:"
        + (50 - half)
        + "%;width:"
        + (half * 2)
        + "%'></i><b style='left:"
        + pos
        + "%'></b></div>";
  }

  static String trend(List<Row> approved) {
    var dates = new TreeMap<String, List<Row>>();
    for (Row r : approved) dates.computeIfAbsent(r.str("day_date"), k -> new ArrayList<>()).add(r);
    if (dates.isEmpty())
      return paper(
          "Tren biaya per porsi harian", "<p>Belum ada nilai terverifikasi pada periode ini.</p>");
    double max = dates.values().stream().mapToDouble(PublicPages::average).max().orElse(1);
    var html = new StringBuilder("<div class='trend'>");
    for (var e : dates.entrySet()) {
      double val = average(e.getValue());
      html.append("<div><small>")
          .append(rp(val))
          .append("</small><i style='height:")
          .append(Math.max(6, 90 * val / max))
          .append("px'></i><small>")
          .append(h(e.getKey().substring(5)))
          .append("</small></div>");
    }
    return paper("Tren biaya per porsi harian", html.append("</div>").toString());
  }

  String directory() throws SQLException {
    StringBuilder b = new StringBuilder();
    for (Row r : rows(p.c, "select id,name,region from kitchen order by name")) {
      if (!(r.str("name") + " " + r.str("region"))
          .toLowerCase(ID)
          .contains(p.q.get("search").toLowerCase(ID))) continue;
      b.append(
          tr(
              link("kitchen&id=" + url(r.str("id")), r.str("name")),
              h(r.str("region")),
              linkBtn("kitchen&id=" + url(r.str("id")), "Detail dapur")));
    }
    return paper(
        "Daftar dapur",
        p.filter("directory", "Cari nama dapur atau wilayah")
            + table("Dapur|Wilayah|Rincian", b.toString())
            + "<p><a router-ignore class='link' href='/export'>Unduh data publik (CSV)</a></p>");
  }

  String detail() throws SQLException {
    Row k = one(p.c, "select * from kitchen where id=?", p.q.get("id"));
    List<Row> set = data().stream().filter(r -> r.str("kitchen").equals(k.str("id"))).toList(),
        ok = releasedOnly(set);
    return link("directory", "← Daftar dapur")
        + title(k.str("name"), k.str("region"))
        + "<div class='metrics'>"
        + kpi(
            "Rata-rata biaya per porsi", ok.isEmpty() ? "—" : rp(average(ok)), "Catatan disetujui")
        + kpi("Hari melapor", "" + set.size(), "Seluruh riwayat")
        + kpi(
            "Sedang diverifikasi",
            "" + set.stream().filter(r -> open(r.str("state"))).count(),
            "Menunggu keputusan")
        + "</div>"
        + trend(ok)
        + paper("Riwayat terakhir", p.list(set, true))
        + linkBtn("report", "Laporkan kejanggalan");
  }
}
