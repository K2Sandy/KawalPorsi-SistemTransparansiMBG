package id.kawalporsi;

import static id.kawalporsi.Database.*;
import static id.kawalporsi.Web.*;

import java.math.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.mindrot.jbcrypt.BCrypt;

final class Service {
  final Database db;

  Service(Database db) {
    this.db = db;
  }

  static final class Session {
    String user = "", csrf = token(), message = "";
    int failures;
    long unlock;
  }

  static Row user(Connection c, Session s) throws SQLException {
    return rows(
            c,
            "select id,name,role,kitchen,regions from account where id=? and active=true",
            s.user)
        .stream()
        .findFirst()
        .orElse(new Row());
  }

  static void require(Row u, String... roles) {
    if (!List.of(roles).contains(u.str("role")))
      throw new SecurityException("Masuk dengan peran yang sesuai untuk membuka halaman ini.");
  }

  static String home(Row u) {
    return switch (u.str("role")) {
      case "PETUGAS" -> "home";
      case "AUDITOR" -> "queue";
      case "ADMIN" -> "overview";
      default -> "login";
    };
  }

  static boolean scope(Row user, Row r) {
    return switch (user.str("role")) {
      case "ADMIN" -> true;
      case "PETUGAS" -> user.str("kitchen").equals(r.str("kitchen"));
      case "AUDITOR" -> Arrays.asList(user.str("regions").split("\\|")).contains(r.str("region"));
      default -> false;
    };
  }

  static final String PURCHASE =
      "select t.*, k.name kitchen_name,k.region from purchase t join kitchen k on k.id=t.kitchen";

  static List<Row> purchases(Connection c, Row u) throws SQLException {
    return rows(c, PURCHASE + " order by t.day_date desc,t.id").stream()
        .filter(
            t ->
                scope(u, t) && (!t.str("state").equals("DRAFT") || u.str("role").equals("PETUGAS")))
        .toList();
  }

  static Row record(Connection c, Row u, String id, boolean lock) throws SQLException {
    Row t = one(c, PURCHASE + " where t.id=?" + (lock ? " for update" : ""), id);
    if (!scope(u, t) || (t.str("state").equals("DRAFT") && !u.str("role").equals("PETUGAS")))
      throw new SecurityException("Tidak memiliki akses ke transaksi ini.");
    return t;
  }

  static LocalDate day(String date) {
    try {
      LocalDate d = LocalDate.parse(date);
      if (d.isAfter(LocalDate.now()) || d.getYear() < 2000) throw new IllegalArgumentException();
      return d;
    } catch (Exception e) {
      throw new IllegalArgumentException("Pilih tanggal kejadian yang valid hingga hari ini.");
    }
  }

  String action(Session session, Params p) {
    if (!session.csrf.equals(p.get("csrf")))
      throw new SecurityException("Sesi formulir berubah. Muat ulang halaman.");
    return db.transaction(
        c -> {
          Row u = user(c, session);
          String op = p.get("op");
          String dest;
          switch (op) {
            case "login" -> {
              if (System.currentTimeMillis() < session.unlock)
                throw new IllegalArgumentException("Tunggu satu menit sebelum mencoba lagi.");
              var found =
                  rows(
                      c,
                      "select * from account where id=? and role=? and active=true",
                      p.get("account"),
                      p.get("role"));
              if (found.isEmpty()
                  || !BCrypt.checkpw(p.get("password"), found.getFirst().str("hash"))) {
                if (++session.failures >= 5) {
                  session.unlock = System.currentTimeMillis() + 60000;
                  session.failures = 0;
                }
                throw new IllegalArgumentException(
                    "Peran, kode pengguna, atau kata sandi tidak cocok.");
              }
              session.user = found.getFirst().str("id");
              session.csrf = token();
              session.failures = 0;
              return home(found.getFirst());
            }
            case "logout" -> {
              session.user = "";
              session.csrf = token();
              return "login";
            }
            case "report" -> {
              String kid = p.required("kitchen", 50);
              one(c, "select id from kitchen where id=?", kid);
              String id = "LAP-" + token().substring(0, 8).toUpperCase(Locale.ROOT);
              update(
                  c,
                  "insert into citizen(id,kitchen,day_date,note,state) values(?,?,?,?,?)",
                  id,
                  kid,
                  day(p.get("date")),
                  p.required("note", 1200),
                  "BARU");
              session.message = "Laporan " + id + " diterima dan tersedia untuk auditor wilayah.";
              return "report";
            }
            case "purchase" -> {
              require(u, "PETUGAS");
              List<String> ids = p.values.getOrDefault("food", List.of()),
                  qty = p.values.getOrDefault("qty", List.of()),
                  amount = p.values.getOrDefault("amount", List.of());
              if (qty.size() != ids.size() || amount.size() != ids.size())
                throw new IllegalArgumentException("Baris bahan belum lengkap.");
              var lines = new ArrayList<Audit.Line>();
              try {
                for (int i = 0; i < ids.size(); i++)
                  lines.add(
                      new Audit.Line(
                          ids.get(i),
                          new BigDecimal(qty.get(i)),
                          Long.parseLong(amount.get(i)),
                          0));
              } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Isi volume dan total rupiah setiap bahan.");
              }
              boolean draft = p.get("mode").equals("draft");
              String id =
                  new Audit()
                      .save(
                          c,
                          u.str("kitchen"),
                          u.str("name"),
                          LocalDate.now(),
                          p.required("menu", 240),
                          (int) p.positive("portions", 1000000),
                          lines,
                          p.files,
                          draft);
              dest = draft ? "entry" : "detail&id=" + url(id);
              session.message = draft ? "Draf disimpan." : "Catatan dikirim dan diperiksa.";
              return dest;
            }
            case "review", "reply" -> {
              boolean reply = op.equals("reply");
              require(u, reply ? "PETUGAS" : "AUDITOR");
              Row t = record(c, u, p.get("id"), true);
              String state = t.str("state"), id = t.str("id");
              String next = reply ? "REVIEW" : p.get("next");
              String note = p.required("note", 1000);
              if (reply) {
                if (!state.equals("WAITING"))
                  throw new IllegalArgumentException("Tidak ada permintaan klarifikasi terbuka.");
                note = p.required("cause", 70) + " — " + note;
                if (p.files.size() > 3)
                  throw new IllegalArgumentException("Maksimal tiga lampiran.");
                for (var f : p.files) Audit.attach(c, id, f);
              } else if (!List.of("FLAGGED", "REVIEW", "WAITING").contains(state)
                  || !List.of("WAITING", "CLEARED", "REJECTED").contains(next)
                  || next.equals(state))
                throw new IllegalArgumentException("Status berubah. Periksa transaksi kembali.");
              update(c, "update purchase set state=? where id=?", next, id);
              String event =
                  switch (next) {
                    case "WAITING" -> "Menunggu klarifikasi";
                    case "REVIEW" -> "Klarifikasi diterima";
                    case "CLEARED" -> "Disetujui auditor";
                    default -> "Ditolak auditor";
                  };
              Audit.log(c, id, u.str("name"), event, note);
              update(c, "insert into alert(purchase,message) values(?,?)", id, event);
              dest = "detail&id=" + url(id);
            }
            case "reportDone" -> {
              require(u, "AUDITOR");
              var r =
                  one(
                      c,
                      "select z.*,k.region from citizen z join kitchen k on k.id=z.kitchen where"
                          + " z.id=? for update",
                      p.get("id"));
              if (!scope(u, r)) throw new SecurityException("Di luar wilayah pengawasan.");
              update(c, "update citizen set state='DITINJAU' where id=?", p.get("id"));
              Audit.log(c, null, u.str("name"), "Laporan warga ditinjau", p.get("id"));
              dest = "reports";
            }
            default -> {
              require(u, "ADMIN");
              dest = admin(c, u, op, p);
            }
          }
          session.message = "Perubahan tersimpan.";
          return dest;
        });
  }

  static String admin(Connection c, Row u, String op, Params p) throws SQLException {
    String note = p.required("reason", 500);
    String result, description;
    switch (op) {
      case "rules" -> {
        String strat = p.get("strategy");
        if (!List.of("PORTION", "UNIT").contains(strat))
          throw new IllegalArgumentException("Pilih strategi.");
        long threshold = p.positive("tolerance", 20);
        update(
            c,
            "update rules set tolerance=?,strategy=?,secondary=?,low_flag=?,notify=? where id=1",
            threshold,
            strat,
            p.on("secondary"),
            p.on("low"),
            p.on("notify"));
        result = "rules";
        description = "Aturan audit: " + strat + ", " + threshold + "%";
      }
      case "price" -> {
        String id = p.get("id"), name = p.required("name", 120), kind = p.get("kind");
        if (!List.of("Basah", "Kering").contains(kind))
          throw new IllegalArgumentException("Pilih jenis bahan.");
        long price = p.positive("price", 100000000);
        if (id.isBlank()) {
          id = "BP-" + token().substring(0, 7);
          update(c, "insert into food(id,name,kind,price) values(?,?,?,?)", id, name, kind, price);
        } else {
          one(c, "select id from food where id=?", id);
          update(c, "update food set name=?,kind=?,price=? where id=?", name, kind, price, id);
        }
        Integer shelf =
            kind.equals("Basah") && !p.get("shelf").isBlank()
                ? (int) p.positive("shelf", 730)
                : null;
        LocalDate expiry =
            kind.equals("Kering") && !p.get("expiry").isBlank()
                ? LocalDate.parse(p.get("expiry"))
                : null;
        update(c, "update food set shelf_days=?,expiry=? where id=?", shelf, expiry, id);
        result = "foods";
        description = "Harga acuan " + name + " = " + rp(price);
      }
      case "isb" -> {
        String region = p.required("region", 90);
        long value = p.positive("isb", 10000000);
        one(c, "select name from region where name=?", region);
        update(c, "update region set isb=? where name=?", value, region);
        result = "isb";
        description = "ISB " + region + " = " + rp(value);
      }
      case "createUser" -> {
        String role = p.get("role");
        String kid = null, region = "";
        if (role.equals("PETUGAS")) {
          kid = p.get("kitchen");
          one(c, "select id from kitchen where id=?", kid);
        } else if (role.equals("AUDITOR")) {
          region = p.get("region");
          one(c, "select name from region where name=?", region);
        } else if (!role.equals("ADMIN")) throw new IllegalArgumentException("Pilih peran.");
        String pass = p.required("pass", 70);
        if (pass.length() < 10)
          throw new IllegalArgumentException("Kata sandi awal minimal 10 karakter.");
        String id = p.required("account", 80);
        if (!rows(c, "select id from account where id=?", id).isEmpty())
          throw new IllegalArgumentException("Kode pengguna sudah terdaftar.");
        update(
            c,
            "insert into account values(?,?,?,?,?,?,true)",
            id,
            p.required("name", 120),
            role,
            kid,
            region,
            BCrypt.hashpw(pass, BCrypt.gensalt(10)));
        result = "users";
        description = "Akun baru " + id;
      }
      case "toggle" -> {
        String id = p.get("id");
        if (id.equals(u.str("id")))
          throw new IllegalArgumentException("Akun Anda sendiri harus tetap aktif.");
        one(c, "select id from account where id=? for update", id);
        update(c, "update account set active=? where id=?", p.get("active").equals("true"), id);
        result = "users";
        description = "Status akun " + id;
      }
      default -> throw new IllegalArgumentException("Tindakan tidak dikenal.");
    }
    Audit.log(c, null, u.str("name"), description, note);
    return result;
  }
}
