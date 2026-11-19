package id.kawalporsi;

import static id.kawalporsi.Database.*;
import static id.kawalporsi.Pages.*;
import static id.kawalporsi.Web.*;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;

final class Entry {
  final Pages p;

  Entry(Pages p) {
    this.p = p;
  }

  String render() throws SQLException {
    String kitchen = p.u.str("kitchen");
    var current =
        rows(
            p.c, "select * from purchase where kitchen=? and day_date=?", kitchen, LocalDate.now());
    Row draft = current.isEmpty() ? new Row() : current.getFirst();
    if (!draft.isEmpty() && !draft.str("state").equals("DRAFT"))
      return title(
              "Catatan hari ini sudah dikirim",
              "Seluruh perubahan berikutnya melalui proses klarifikasi.")
          + linkBtn(details(draft), "Buka transaksi");
    List<Row> lines =
        rows(p.c, "select * from item where purchase=? order by seq", draft.str("id"));
    if (lines.isEmpty() && p.q.get("copy").equals("1")) {
      var last =
          rows(
              p.c,
              "select * from purchase where kitchen=? and day_date<? order by day_date desc limit"
                  + " 1",
              kitchen,
              LocalDate.now());
      if (!last.isEmpty()) {
        draft = last.getFirst();
        lines = rows(p.c, "select * from item where purchase=? order by seq", draft.str("id"));
      }
    }
    var foods = rows(p.c, "select * from food order by name");
    StringBuilder entries = new StringBuilder();
    if (lines.isEmpty()) entries.append(line(foods, new Row()));
    else for (Row r : lines) entries.append(line(foods, r));
    var setting = one(p.c, "select * from rules");
    long isb =
        one(
                p.c,
                "select r.isb from kitchen k join region r on k.region=r.name where k.id=?",
                kitchen)
            .num("isb");
    String meta =
        paper(
            "①  Porsi hari ini",
            "<p>Jumlah porsi yang benar-benar dimasak dan dibagikan.</p><div class='pair'>"
                + field(
                    "Porsi dimasak",
                    input(
                        "portions",
                        draft.str("portions"),
                        "number",
                        "min='1' max='1000000' required placeholder='1.120'"))
                + field(
                    "Tanggal belanja",
                    input("date", LocalDate.now().toString(), "date", "readonly"))
                + "</div>"
                + field(
                    "Menu hari ini",
                    input(
                        "menu",
                        draft.str("menu"),
                        "text",
                        "required maxlength='240' placeholder='Nasi, ayam, sayur, dan buah'")));
    String items =
        paper(
            "②  Rincian belanja bahan",
            "<p>Satu baris untuk tiap bahan. "
                + link("entry&copy=1", "Salin dari catatan terakhir")
                + "</p><div id='lines'>"
                + entries
                + "</div><template id='line-template'>"
                + line(foods, new Row())
                + "</template><button class='btn' type='button' data-add>＋ Tambah bahan</button>");
    String evidence =
        paper(
            "③  Bukti nota belanja",
            field(
                    "Pilih nota JPG, PNG, atau PDF (maks. 10 MB per file)",
                    input(
                        "receipt",
                        "",
                        "file",
                        "multiple accept='image/jpeg,image/png,application/pdf'"))
                + (!current.isEmpty() ? attachments(p.c, current.getFirst().str("id")) : ""));
    double tolerance = setting.dec("tolerance").doubleValue();
    double gaugeStart = setting.yes("low_flag") ? 50 - 2 * tolerance : 50;
    String summary =
        paper(
            "Ringkasan otomatis",
            "<small>Biaya bahan per porsi</small><h2 class='big' id='cost'>—</h2><div"
                + " class='gauge'><i style='left:"
                + gaugeStart
                + "%;width:"
                + (50 + 2 * tolerance - gaugeStart)
                + "%'></i><b id='needle'></b></div><p>ISB"
                + " daerah <b>"
                + rp(isb)
                + "</b></p><div class='notice' id='evaluation'>Isi rincian belanja.</div><p>Total"
                + " belanja <b id='total'>—</b></p><p>Batas toleransi <b>±"
                + setting.dec("tolerance").stripTrailingZeros().toPlainString()
                + "%</b></p>");
    return title(
            "Catat belanja hari ini",
            "Isi bahan yang dibeli dan jumlah porsi yang dimasak. Biaya per porsi dihitung"
                + " otomatis.")
        + p.form("purchase", true)
        + "<div class='columns entry' data-isb='"
        + isb
        + "' data-threshold='"
        + setting.dec("tolerance")
        + "'><div>"
        + meta
        + items
        + evidence
        + "</div><aside>"
        + summary
        + "<button class='primary wide' name='mode' value='send'>Kirim untuk"
        + " diaudit</button><button class='btn wide' name='mode' value='draft'>Simpan"
        + " draf</button><small>Setelah dikirim, data terkunci di jejak"
        + " audit.</small></aside></div></form>";
  }

  static String line(List<Row> foods, Row current) {
    var options = new StringBuilder("<option value=''>Pilih bahan</option>");
    for (var f : foods)
      options
          .append("<option data-kind='")
          .append(h(f.str("kind")))
          .append("' data-price='")
          .append(f.num("price"))
          .append("' value='")
          .append(h(f.str("id")))
          .append("' ")
          .append(f.str("id").equals(current.str("food")) ? "selected" : "")
          .append(">")
          .append(h(f.str("name")))
          .append("</option>");
    return "<div class='line'><div class='lineFields'>"
        + field("Bahan", "<select name='food' required>" + options + "</select>")
        + field(
            "Volume (kg)",
            input(
                "qty",
                current.str("qty"),
                "number",
                "step='0.001' min='0.001' max='1000000' required"))
        + field(
            "Total harga (Rp)",
            input(
                "amount",
                current.str("amount"),
                "number",
                "step='1' min='1' max='100000000000' required"))
        + "<button type='button' data-remove aria-label='Hapus bahan'>×</button></div><small"
        + " data-reference></small></div>";
  }

  static String attachments(Connection c, String id) throws SQLException {
    StringBuilder b = new StringBuilder();
    for (Row r : rows(c, "select id,name from evidence where purchase=? order by name", id))
      b.append("<p><a router-ignore class='link' href='/file?id=")
          .append(url(r.str("id")))
          .append("'>")
          .append(icon("50310"))
          .append(h(r.str("name")))
          .append("</a></p>");
    return b.isEmpty() ? "<small>Belum ada lampiran.</small>" : b.toString();
  }
}
