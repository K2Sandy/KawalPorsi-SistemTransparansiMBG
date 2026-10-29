package id.kawalporsi;

import static id.kawalporsi.Database.*;
import static id.kawalporsi.Pages.*;
import static id.kawalporsi.Service.*;
import static id.kawalporsi.Web.*;

import java.sql.*;
import java.util.*;

final class Staff {
  final Pages p;

  Staff(Pages p) {
    this.p = p;
  }

  String render() throws SQLException {
    var all = purchases(p.c, p.u);
    String v = p.v;
    long waiting = all.stream().filter(r -> r.str("state").equals("WAITING")).count();
    var active = all.stream().filter(r -> open(r.str("state"))).toList();
    if (v.equals("trail") || v.equals("alerts")) return events(all, v.equals("alerts"));
    if (v.equals("reports")) return reports();
    if (v.equals("kitchens")) {
      var b = new StringBuilder();
      for (Row r : rows(p.c, "select id kitchen,name,region from kitchen order by name"))
        if (scope(p.u, r))
          b.append(
              tr(
                  h(r.str("name")),
                  h(r.str("region")),
                  link("all&kid=" + url(r.str("kitchen")), "Lihat transaksi")));
      return title("Dapur", "Dapur di dalam wilayah pengawasan Anda.")
          + paper("Dapur dalam pengawasan", table("Dapur|Wilayah|Transaksi", b.toString()));
    }
    if (v.equals("home"))
      return title(
              "Selamat datang, " + p.u.str("name").split(" ")[0],
              "Ringkasan catatan belanja dan klarifikasi dapur Anda.")
          + "<div class='columns'><div>"
          + paper(
              "Catatan belanja hari ini",
              "<p>Isi porsi, rincian belanja, dan bukti nota untuk hari ini.</p>"
                  + a("entry", "Buka catatan hari ini", "primary"))
          + "</div><div>"
          + paper(
              "Perlu tindakan",
              "<h2>"
                  + waiting
                  + " klarifikasi menunggu jawaban</h2>"
                  + link("clarify", "Lihat klarifikasi →"))
          + "</div></div><div class='metrics'>"
          + kpi(
              "Catatan terkirim",
              "" + all.stream().filter(r -> !r.str("state").equals("DRAFT")).count(),
              "Riwayat dapur")
          + kpi("Klaim ditahan", "" + active.size(), "Dalam pemeriksaan")
          + kpi(
              "Biaya per porsi disetujui",
              releasedCount(all) == 0
                  ? "—"
                  : rp(PublicPages.average(PublicPages.releasedOnly(all))),
              "Rata-rata tertimbang")
          + "</div>"
          + paper("Riwayat terakhir", p.list(all.stream().limit(6).toList(), false));
    String head = LABEL.get(v), summary = "", state = p.q.get("state");
    List<Row> base = all;
    if (v.equals("queue")) {
      summary =
          "<div class='metrics'>"
              + kpi(
                  "Perlu ditinjau",
                  "" + active.stream().filter(r -> !r.str("state").equals("WAITING")).count(),
                  "Antrean wilayah")
              + kpi("Menunggu klarifikasi", "" + waiting, "Menunggu petugas dapur")
              + kpi(
                  "Klaim dibekukan",
                  rp(active.stream().mapToLong(r -> r.num("total")).sum()),
                  "Selama pemeriksaan")
              + "</div><div class='toolbar'>"
              + linkBtn("queue", "Belum selesai")
              + linkBtn("queue&state=closed", "Selesai")
              + linkBtn("queue&state=any", "Semua")
              + "</div>";
      base =
          all.stream()
              .filter(
                  r ->
                      state.equals("any")
                          || (state.equals("closed")
                              ? List.of("REJECTED", "CLEARED").contains(r.str("state"))
                              : open(r.str("state"))))
              .sorted(Comparator.comparingDouble((Row r) -> Math.abs(diff(r))).reversed())
              .toList();
    } else if (v.equals("clarify"))
      base =
          all.stream()
              .filter(r -> r.str("state").equals("WAITING") || r.str("state").equals("REVIEW"))
              .toList();
    String key = p.q.get("search").toLowerCase(ID), kid = p.q.get("kid");
    var filtered =
        base.stream()
            .filter(
                r ->
                    (kid.isEmpty() || kid.equals(r.str("kitchen")))
                        && (r.str("id") + " " + r.str("kitchen_name") + " " + r.str("region"))
                            .toLowerCase(ID)
                            .contains(key))
            .toList();
    return title(
            head,
            v.equals("queue")
                ? "Deviasi terbesar ditampilkan lebih dulu. Buka transaksi untuk memeriksa bukti."
                : "Buka transaksi untuk melihat rincian belanja dan status.")
        + summary
        + paper(
            head,
            "<form class='filters' method='get'>"
                + hidden("v", v)
                + hidden("state", state)
                + hidden("kid", kid)
                + input(
                    "search",
                    key,
                    "search",
                    "aria-label='Cari' placeholder='Cari ID atau nama dapur'")
                + button("Cari")
                + "</form>"
                + p.list(filtered, false));
  }

  int releasedCount(List<Row> all) {
    return PublicPages.releasedOnly(all).size();
  }

  String reports() throws SQLException {
    var b = new StringBuilder();
    for (Row r :
        rows(
            p.c,
            "select z.*,k.region,k.name from citizen z join kitchen k on k.id=z.kitchen order by"
                + " z.stamp desc"))
      if (scope(p.u, r))
        b.append(
            tr(
                h(r.str("id")),
                h(r.str("name")),
                h(r.str("day_date")),
                h(r.str("note")),
                h(r.str("state")),
                r.str("state").equals("BARU")
                    ? p.form("reportDone", false)
                        + hidden("id", r.str("id"))
                        + button("Tandai ditinjau")
                        + "</form>"
                    : "—"));
    return title("Laporan warga", "Laporan yang masuk dari masyarakat di wilayah Anda.")
        + paper(
            "Kotak masuk",
            table("Kode|Dapur|Tanggal kejadian|Laporan|Status|Tindakan", b.toString()));
  }

  String events(List<Row> all, boolean alerts) throws SQLException {
    var ids = new HashSet<String>();
    all.forEach(r -> ids.add(r.str("id")));
    var b = new StringBuilder();
    for (Row e :
        rows(
            p.c,
            alerts
                ? "select purchase,stamp,message action from alert order by seq desc"
                : "select * from event order by seq desc")) {
      String id = e.str("purchase");
      if (!ids.contains(id) && !(id.isEmpty() && p.u.str("role").equals("ADMIN"))) continue;
      b.append(
          tr(
              h(e.str("stamp").substring(0, 16)),
              id.isEmpty() ? "Konfigurasi" : link("detail&id=" + url(id), id),
              h(e.str("action")),
              h(e.str("actor")),
              h(e.str("note"))));
    }
    return title(
            alerts ? "Notifikasi" : "Jejak audit",
            alerts
                ? "Pesan dari pemeriksaan transaksi di wilayah Anda."
                : "Riwayat tindakan tersimpan menurut waktu kejadian.")
        + paper(
            "Aktivitas terbaru", table("Waktu|Transaksi|Tindakan|Pelaku|Catatan", b.toString()));
  }

  String detail() throws SQLException {
    Row t = record(p.c, p.u, p.q.get("id"), false);
    String id = t.str("id"), state = t.str("state"), role = p.u.str("role");
    StringBuilder items = new StringBuilder(), timeline = new StringBuilder();
    for (Row r : rows(p.c, "select * from item where purchase=? order by seq", id))
      items.append(
          tr(
              h(r.str("label")),
              h(r.dec("qty").stripTrailingZeros().toPlainString()),
              rp(r.num("amount")),
              rp(r.num("amount") / r.dec("qty").doubleValue()),
              rp(r.num("reference_price"))));
    for (Row r : rows(p.c, "select * from event where purchase=? order by seq desc", id))
      timeline
          .append("<article class='event'><small>")
          .append(h(r.str("stamp").substring(0, 16)))
          .append(" · ")
          .append(h(r.str("actor")))
          .append("</small><b>")
          .append(h(r.str("action")))
          .append("</b><p>")
          .append(h(r.str("note")))
          .append("</p></article>");
    String actions = "";
    if (role.equals("AUDITOR") && open(state))
      actions =
          paper(
              "Keputusan auditor",
              p.form("review", false)
                  + hidden("id", id)
                  + field(
                      "Tindakan",
                      "<select name='next'>"
                          + (state.equals("WAITING")
                              ? ""
                              : option("WAITING", "Minta klarifikasi", ""))
                          + option("CLEARED", "Setujui dan buka klaim", "")
                          + option("REJECTED", "Tolak klaim", "")
                          + "</select>")
                  + field(
                      "Pertanyaan atau alasan keputusan",
                      "<textarea name='note' required maxlength='1000'></textarea>")
                  + button("Simpan keputusan")
                  + "</form>");
    if (role.equals("PETUGAS") && state.equals("WAITING"))
      actions =
          paper(
              "Jawaban Anda",
              p.form("reply", true)
                  + hidden("id", id)
                  + field(
                      "Penyebab utama",
                      "<select name='cause'><option>Kenaikan harga pasar</option><option>Kesalahan"
                          + " pencatatan</option><option>Jumlah porsi"
                          + " berubah</option><option>Lainnya</option></select>")
                  + field(
                      "Penjelasan", "<textarea name='note' maxlength='1000' required></textarea>")
                  + field(
                      "Lampiran bukti",
                      input(
                          "receipt",
                          "",
                          "file",
                          "multiple accept='image/jpeg,image/png,application/pdf'"))
                  + button("Kirim klarifikasi")
                  + "</form>");
    return title(
            role.equals("PETUGAS") && state.equals("WAITING")
                ? "Klarifikasi transaksi"
                : t.str("kitchen_name"),
            id + " · " + t.str("day_date") + " · " + t.str("menu"))
        + badge(state)
        + (open(state) ? notice("Klaim anggaran dibekukan sementara. " + t.str("reason")) : "")
        + "<div class='metrics'>"
        + kpi(
            "Biaya per porsi",
            rp(cost(t)),
            rp(t.num("total")) + " ÷ " + num(t.num("portions")) + " porsi")
        + kpi("ISB " + t.str("region"), rp(t.num("isb")), "Acuan saat transaksi dikirim")
        + kpi(
            "Deviasi",
            pct(diff(t)),
            "Ambang ±" + t.dec("tolerance").stripTrailingZeros().toPlainString() + "%")
        + "</div><div class='columns'><div>"
        + paper(
            "Rincian belanja",
            table("Bahan|Volume (kg)|Total|Harga/kg|Acuan/kg", items.toString())
                + "<small>Strategi: "
                + h(t.str("strategy"))
                + "</small>")
        + paper("Bukti nota", Entry.attachments(p.c, id))
        + actions
        + "</div><div>"
        + paper("Jejak audit", timeline.toString())
        + "</div></div>";
  }
}
