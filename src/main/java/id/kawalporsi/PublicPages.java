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
}
