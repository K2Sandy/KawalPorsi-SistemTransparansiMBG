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
}
