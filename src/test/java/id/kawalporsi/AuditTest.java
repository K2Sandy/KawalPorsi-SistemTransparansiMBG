package id.kawalporsi;

import static id.kawalporsi.Database.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class AuditTest {
  private static Audit.TransaksiPengadaan sample(long total, boolean low) {
    return new Audit.TransaksiPengadaan(List.of(), 100, total, 10000, new BigDecimal("5"), low);
  }

  @Test
  void portionBoundaryUsesExactArithmetic() {
    var strategy = new Audit.RasioPorsiStrategy();
    assertTrue(strategy.evaluate(sample(1050000, true)).isEmpty());
    assertTrue(strategy.evaluate(sample(950000, true)).isEmpty());
    assertFalse(strategy.evaluate(sample(1050001, true)).isEmpty());
    assertFalse(strategy.evaluate(sample(949999, true)).isEmpty());
    assertTrue(strategy.evaluate(sample(949999, false)).isEmpty());
  }

  @Test
  void individualMarkupCanFlagAnOtherwiseAcceptableMeal() {
    var meal =
        new Audit.TransaksiPengadaan(
            List.of(new Audit.Line("Beras", BigDecimal.ONE, 12000, 10000)),
            1,
            12000,
            12000,
            new BigDecimal("5"),
            true);
    assertTrue(new Audit.RasioPorsiStrategy().evaluate(meal).isEmpty());
    assertEquals(1, new Audit.DeviasiHargaStrategy().evaluate(meal).size());
  }

  @Test
  void submissionSnapshotsAndLocksHistory() {
    Database db = new Database("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    db.initialize();
    db.transaction(
        c -> {
          var engine = new Audit();
          var lines = List.of(new Audit.Line("BP-1", new BigDecimal("10"), 135000, 0));
          var day = LocalDate.now();
          String id = engine.save(c, "AB01", "Petugas", day, "Nasi", 10, lines, List.of(), true);
          assertEquals(
              id, engine.save(c, "AB01", "Petugas", day, "Nasi", 10, lines, List.of(), false));
          assertEquals("FLAGGED", one(c, "select state from purchase where id=?", id).str("state"));
          assertEquals(1, one(c, "select count(*) n from alert where purchase=?", id).num("n"));
          update(c, "update region set isb=11000 where name='Aceh Besar'");
          update(c, "update food set price=14000 where id='BP-1'");
          assertEquals(10000, one(c, "select isb from purchase where id=?", id).num("isb"));
          assertEquals(
              13500,
              one(c, "select reference_price from item where purchase=?", id)
                  .num("reference_price"));
          assertThrows(
              IllegalArgumentException.class,
              () -> engine.save(c, "AB01", "Petugas", day, "Baru", 10, lines, List.of(), false));
          return null;
        });
  }

  @Test
  void persistentDatabaseSurvivesAnotherApplicationInstance(
      @org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
    String url = "jdbc:h2:file:" + dir.resolve("kawal");
    Database first = new Database(url);
    first.initialize();
    first.transaction(
        c -> {
          update(c, "update region set isb=11200 where name='Aceh Besar'");
          return null;
        });
    Database reopened = new Database(url);
    reopened.initialize();
    long isb =
        reopened.transaction(
            c -> one(c, "select isb from region where name='Aceh Besar'").num("isb"));
    assertEquals(11200L, isb);
  }
}
