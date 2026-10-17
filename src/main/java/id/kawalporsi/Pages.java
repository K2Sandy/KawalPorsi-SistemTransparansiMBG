package id.kawalporsi;

import static id.kawalporsi.Database.*;
import static id.kawalporsi.Service.*;
import static id.kawalporsi.Web.*;

import java.sql.*;
import java.time.*;
import java.util.*;

/** Per-request view context. All user-entered text is escaped at the rendering boundary. */
class Pages {
  final Connection c;
  final Row u;
  final Session s;
  final Params q;
  final String v;

  Pages(Connection c, Session s, Params q) throws SQLException {
    this.c = c;
    this.s = s;
    this.q = q;
    u = user(c, s);
    v = q.get("v").isEmpty() ? home(u) : q.get("v");
  }

  static final Map<String, String> LABEL =
      Map.ofEntries(
          Map.entry("home", "Beranda"),
          Map.entry("entry", "Catat belanja"),
          Map.entry("history", "Riwayat transaksi"),
          Map.entry("clarify", "Klarifikasi"),
          Map.entry("queue", "Antrean anomali"),
          Map.entry("all", "Semua transaksi"),
          Map.entry("kitchens", "Dapur"),
          Map.entry("trail", "Jejak audit"),
          Map.entry("reports", "Laporan warga"),
          Map.entry("overview", "Ringkasan"),
          Map.entry("foods", "Bahan & harga acuan"),
          Map.entry("isb", "ISB daerah"),
          Map.entry("rules", "Pengaturan audit"),
          Map.entry("users", "Pengguna & dapur"),
          Map.entry("detail", "Detail transaksi"),
          Map.entry("alerts", "Notifikasi"),
          Map.entry("guide", "Panduan & bantuan"));

  String form(String op, boolean files) {
    return "<form method='post' action='/action' "
        + (files ? "enctype='multipart/form-data'" : "")
        + ">"
        + hidden("csrf", s.csrf)
        + hidden("op", op);
  }

  static String hidden(String key, String val) {
    return input(key, val, "hidden", "");
  }

  static String button(String name) {
    return "<button class='primary' type='submit'>" + h(name) + "</button>";
  }

  static String notice(String text) {
    return "<div class='notice' role='status'>" + h(text) + "</div>";
  }

  static String logo() {
    return "<span class='brand'><i>" + icon("9128e") + "</i>KawalPorsi</span>";
  }

  static String link(String view, String name) {
    return a(view, h(name), "link");
  }

  static String linkBtn(String view, String name) {
    return a(view, h(name), "btn");
  }

  static String kpi(String name, String value, String caption) {
    return "<div class='kpi'><small>"
        + h(name)
        + "</small><strong>"
        + h(value)
        + "</strong><small>"
        + h(caption)
        + "</small></div>";
  }

  String filter(String view, String placeholder) {
    return "<form class='filters' method='get'>"
        + hidden("v", view)
        + input(
            "search",
            q.get("search"),
            "search",
            "placeholder='" + h(placeholder) + "' aria-label='" + h(placeholder) + "'")
        + button("Cari")
        + "</form>";
  }

  String opts(String sql, String current) throws SQLException {
    StringBuilder b = new StringBuilder();
    for (Row r : rows(c, sql)) b.append(option(r.str("id"), r.str("name"), current));
    return b.toString();
  }
}
