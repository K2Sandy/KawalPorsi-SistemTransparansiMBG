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
}
