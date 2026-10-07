package id.kawalporsi;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import org.mindrot.jbcrypt.BCrypt;

/** JDBC unit of work. All records for one submission commit or roll back together. */
public final class Database {
  public interface Work<T> {
    T run(Connection c) throws Exception;
  }

  private final String url;

  public Database(String url) {
    this.url = url;
  }

  public <T> T transaction(Work<T> work) {
    try (Connection c = DriverManager.getConnection(url, "sa", "")) {
      c.setAutoCommit(false);
      try {
        T result = work.run(c);
        c.commit();
        return result;
      } catch (Exception ex) {
        c.rollback();
        throw ex;
      }
    } catch (RuntimeException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalStateException("Operasi basis data gagal.", ex);
    }
  }

  static PreparedStatement prepare(Connection c, String sql, Object... args) throws SQLException {
    PreparedStatement s = c.prepareStatement(sql);
    for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
    return s;
  }

  static void update(Connection c, String sql, Object... args) throws SQLException {
    try (var s = prepare(c, sql, args)) {
      s.executeUpdate();
    }
  }

  static class Row extends LinkedHashMap<String, Object> {
    String str(String key) {
      return Objects.toString(get(key), "");
    }

    long num(String key) {
      return ((Number) get(key)).longValue();
    }

    boolean yes(String key) {
      return Boolean.TRUE.equals(get(key));
    }

    java.math.BigDecimal dec(String key) {
      return new java.math.BigDecimal(str(key));
    }
  }

  static List<Row> rows(Connection c, String sql, Object... args) throws SQLException {
    try (var s = prepare(c, sql, args);
        var r = s.executeQuery()) {
      var list = new ArrayList<Row>();
      var meta = r.getMetaData();
      while (r.next()) {
        var row = new Row();
        for (int i = 1; i <= meta.getColumnCount(); i++)
          row.put(meta.getColumnLabel(i).toLowerCase(Locale.ROOT), r.getObject(i));
        list.add(row);
      }
      return list;
    }
  }

  static Row one(Connection c, String sql, Object... args) throws SQLException {
    return rows(c, sql, args).stream()
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Data tidak tersedia."));
  }

  void initialize() {
    transaction(
        c -> {
          String schema =
              new String(
                  Objects.requireNonNull(getClass().getResourceAsStream("/schema.sql"))
                      .readAllBytes(),
                  java.nio.charset.StandardCharsets.UTF_8);
          for (String ddl : schema.split(";")) if (!ddl.isBlank()) update(c, ddl);
          if (one(c, "select count(*) n from rules").num("n") == 0) seed(c);
          return null;
        });
  }

  private void seed(Connection c) throws Exception {
    update(c, "insert into rules values(1,5,'PORTION',true,true,true)");
    update(c, "insert into region values('Aceh Besar',10000),('Banda Aceh',10400),('Pidie',9800)");
    String[][] kitchens = {
      {"AB01", "SPPG Lambaro 01", "Aceh Besar"},
      {"AB02", "SPPG Lhoknga 02", "Aceh Besar"},
      {"BA01", "SPPG Meuraxa 03", "Banda Aceh"},
      {"BA02", "SPPG Kuta Alam 01", "Banda Aceh"},
      {"PD01", "SPPG Sigli 01", "Pidie"}
    };
    for (var k : kitchens) update(c, "insert into kitchen values(?,?,?)", (Object[]) k);
    String hash = BCrypt.hashpw("KawalPorsi2026!", BCrypt.gensalt(10));
    update(
        c,
        "insert into account values(?,?,?,?,?,?,true)",
        "SPPG-AB-0107",
        "Rahmah Aulia",
        "PETUGAS",
        "AB01",
        "",
        hash);
    update(
        c,
        "insert into account values(?,?,?,?,?,?,true)",
        "198804122014",
        "Syarifah Maulida",
        "AUDITOR",
        null,
        "Aceh Besar|Banda Aceh",
        hash);
    update(
        c,
        "insert into account values(?,?,?,?,?,?,true)",
        "198203152008",
        "Andi Firmansyah",
        "ADMIN",
        null,
        "",
        hash);
    String[] foods = {
      "Beras medium",
      "Daging ayam ras",
      "Telur ayam",
      "Bayam dan wortel",
      "Pisang kepok",
      "Minyak goreng"
    };
    int[] prices = {13500, 35000, 28000, 14000, 15000, 18000};
    for (int i = 0; i < foods.length; i++)
      update(
          c,
          "insert into food(id,name,kind,price) values(?,?,?,?)",
          "BP-" + (i + 1),
          foods[i],
          i == 0 || i == 5 ? "Kering" : "Basah",
          prices[i]);
    update(
        c,
        "update food set shelf_days=case id when 'BP-2' then 2 when 'BP-3' then 14 when 'BP-4' then"
            + " 1 when 'BP-5' then 4 end where kind='Basah'");
    var audit = new Audit();
    for (int d = 6; d >= 1; d--)
      for (int k = 0; k < kitchens.length; k++) {
        String kit = kitchens[k][0];
        int portions = 1000;
        long total =
            (k == 2 || k == 3 ? 10400 : k == 4 ? 9800 : 10000) * 1000L + (d % 3 - 1) * 90000;
        if (d == 1 && (k == 0 || k == 1)) total += 1500000;
        var lines = new ArrayList<Audit.Line>();
        long rice = 2700000;
        lines.add(new Audit.Line("BP-1", new java.math.BigDecimal("200"), rice, 13500));
        var chickenQuantity =
            java.math.BigDecimal.valueOf(total - rice)
                .divide(java.math.BigDecimal.valueOf(35000), 3, java.math.RoundingMode.HALF_UP);
        lines.add(new Audit.Line("BP-2", chickenQuantity, total - rice, 35000));
        String id =
            audit.save(
                c,
                kit,
                "Data contoh",
                LocalDate.now().minusDays(d),
                "Nasi dan ayam",
                portions,
                lines,
                List.of(),
                false);
        if (d == 1 && k == 0) {
          update(c, "update purchase set state='WAITING' where id=?", id);
          Audit.log(
              c,
              id,
              "Auditor contoh",
              "Menunggu klarifikasi",
              "Mohon jelaskan kenaikan biaya per porsi.");
        }
      }
  }
}
