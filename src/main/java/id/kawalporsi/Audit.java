package id.kawalporsi;

import static id.kawalporsi.Database.*;

import java.math.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public final class Audit {
  record Line(String food, BigDecimal qty, long amount, long reference) {}

  record Receipt(String name, String mime, byte[] data) {}

  record TransaksiPengadaan(
      List<Line> lines, int portions, long total, long isb, BigDecimal tolerance, boolean low) {}

  interface AuditStrategy {
    List<String> evaluate(TransaksiPengadaan ctx);
  }

  static boolean exceeds(BigDecimal actual, BigDecimal base, TransaksiPengadaan ctx) {
    BigDecimal delta = actual.subtract(base);
    if (ctx.low()) delta = delta.abs();
    // Cross multiplication preserves the exact inclusive threshold.
    return delta.multiply(BigDecimal.valueOf(100)).compareTo(base.multiply(ctx.tolerance())) > 0;
  }

  static class RasioPorsiStrategy implements AuditStrategy {
    public List<String> evaluate(TransaksiPengadaan c) {
      boolean flag =
          exceeds(
              BigDecimal.valueOf(c.total()),
              BigDecimal.valueOf(c.isb()).multiply(BigDecimal.valueOf(c.portions())),
              c);
      return flag ? List.of("Biaya per porsi melewati ambang ISB daerah.") : List.of();
    }
  }
}
