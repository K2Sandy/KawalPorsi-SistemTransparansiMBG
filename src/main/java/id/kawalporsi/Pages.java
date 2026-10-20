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

  String render() throws SQLException {
    String message = s.message;
    s.message = "";
    String flash = message.isEmpty() ? "" : notice(message);
    if (v.equals("login")) return login(flash);
    if (List.of("public", "directory", "kitchen", "method", "report").contains(v))
      return new PublicPages(this).render(flash);
    String content =
        switch (v) {
          case "home", "entry", "history", "clarify" -> {
            require(u, "PETUGAS");
            yield v.equals("entry") ? new Entry(this).render() : new Staff(this).render();
          }
          case "queue", "all", "kitchens", "reports" -> {
            require(u, "AUDITOR");
            yield new Staff(this).render();
          }
          case "trail", "alerts" -> {
            require(u, "AUDITOR", "ADMIN", "PETUGAS");
            yield new Staff(this).render();
          }
          case "detail" -> {
            require(u, "PETUGAS", "AUDITOR", "ADMIN");
            yield new Staff(this).detail();
          }
          case "overview", "foods", "isb", "rules", "users" -> {
            require(u, "ADMIN");
            yield new Admin(this).render();
          }
          case "guide" -> {
            require(u, "PETUGAS", "AUDITOR", "ADMIN");
            yield title("Panduan & bantuan", "Alur kerja KawalPorsi")
                + paper(
                    "Alur transaksi",
                    "<ol><li>Petugas mengisi belanja dan porsi lalu mengirimnya.</li><li>Sistem"
                        + " membandingkan biaya dengan acuan. Klaim yang ditandai"
                        + " ditahan.</li><li>Auditor memeriksa nota, meminta klarifikasi, lalu"
                        + " mengambil keputusan.</li></ol>"
                        + link("method", "Cara membaca data publik"));
          }
          default -> throw new IllegalArgumentException("Halaman tidak ditemukan.");
        };
    return shell(flash + content);
  }

  String login(String flash) throws SQLException {
    String role = List.of("AUDITOR", "ADMIN").contains(q.get("role")) ? q.get("role") : "PETUGAS";
    StringBuilder tabs = new StringBuilder("<div class='tabs' aria-label='Masuk sebagai'>");
    for (var r : new String[] {"PETUGAS", "AUDITOR", "ADMIN"})
      tabs.append(a("login&role=" + r, h(roleName(r)), r.equals(role) ? "selected" : ""));
    tabs.append("</div>");
    String account =
        switch (role) {
          case "AUDITOR" -> "198804122014";
          case "ADMIN" -> "198203152008";
          default -> "SPPG-AB-0107";
        };
    String latest = new PublicPages(this).latest();
    return "<div class='login'><div class='loginLeft'><div class='loginForm'>"
        + logo()
        + title("Masuk ke KawalPorsi", "Untuk petugas dapur, auditor, dan admin program MBG.")
        + flash
        + "<strong class='fieldLabel'>Masuk sebagai</strong>"
        + tabs
        + form("login", false)
        + hidden("role", role)
        + field(
            role.equals("PETUGAS") ? "Kode dapur (SPPG)" : "Nomor induk pegawai (NIP)",
            input("account", account, "text", "required autocomplete='username'"))
        + field(
            "Kata sandi",
            "<div class='password'>"
                + input("password", "", "password", "required autocomplete='current-password'")
                + "<button type='button' data-password>Tampilkan</button></div>")
        + "<details><summary>Akun demo & bantuan masuk</summary><p>Kata sandi ketiga akun demo:"
        + " <code>KawalPorsi2026!</code>. Kode pengguna mengikuti peran yang dipilih.</p></details>"
        + button("Masuk")
        + "</form><div class='or'>atau</div>"
        + a(
            "public",
            icon("a11ae")
                + "<span><b>Lihat dasbor publik</b><small>Tanpa akun. Untuk orang tua, sekolah, dan"
                + " masyarakat.</small></span>"
                + icon("2c1b9"),
            "publicLink")
        + "</div><footer>Kelompok 10 · Informatika USK · 2026</footer></div><aside"
        + " class='loginRight'><div><h1>Setiap rupiah belanja dapur MBG tercatat dan bisa dicek"
        + " siapa saja.</h1><p>Biaya bahan per porsi dibandingkan otomatis dengan Indeks Standar"
        + " Biaya daerah. Yang janggal ditandai untuk diperiksa auditor, bukan langsung"
        + " dituduh.</p>"
        + latest
        + "</div></aside></div>";
  }

  static String roleName(String role) {
    return switch (role) {
      case "PETUGAS" -> "Petugas Dapur";
      case "AUDITOR" -> "Auditor";
      default -> "Admin";
    };
  }

  String shell(String body) throws SQLException {
    String role = u.str("role");
    String[] nav =
        switch (role) {
          case "PETUGAS" ->
              new String[] {"home:96993", "entry:301b6", "history:b17db", "clarify:b6644"};
          case "AUDITOR" ->
              new String[] {
                "queue:b19c0", "all:301b6", "kitchens:06969", "trail:b17db", "reports:b6644"
              };
          default ->
              new String[] {
                "overview:96993", "foods:fd5b9", "isb:ea48b", "rules:e3f69", "users:f4e66"
              };
        };
    StringBuilder links = new StringBuilder();
    for (String item : nav) {
      String[] bits = item.split(":");
      links.append(
          a(bits[0], icon(bits[1]) + h(LABEL.get(bits[0])), bits[0].equals(v) ? "active" : ""));
    }
    String scope =
        switch (role) {
          case "PETUGAS" ->
              one(c, "select name,region from kitchen where id=?", u.str("kitchen")).str("name");
          case "AUDITOR" -> u.str("regions").replace("|", " · ");
          default -> "Provinsi Aceh";
        };
    return "<div class='shell'><aside class='sidebar'>"
        + logo()
        + "<div class='context'><small>"
        + (role.equals("PETUGAS") ? "Dapur" : "Lingkup pengawasan")
        + "</small>"
        + h(scope)
        + "</div><nav>"
        + links
        + "</nav><div class='sidefoot'>"
        + a("guide", icon("55b81") + "Panduan & bantuan", "")
        + link("public", "Dasbor publik")
        + "<hr><b>"
        + h(u.str("name"))
        + "</b><small>"
        + roleName(role)
        + "</small>"
        + form("logout", false)
        + "<button class='textBtn'>Keluar</button></form></div></aside><div class='main'><div"
        + " class='topbar'><b>"
        + h(LABEL.getOrDefault(v, v))
        + "</b><div><span class='role'>"
        + roleName(role)
        + "</span><span class='dateChip'>"
        + date(LocalDate.now())
        + "</span>"
        + a("alerts", icon("3256c") + "<span class='sr'>Notifikasi</span>", "iconBtn")
        + "</div></div><main><a router-ignore class='liveAlert' role='status' data-alert-update"
        + " hidden href='/?v=alerts'></a>"
        + body
        + "</main></div></div>";
  }
}
