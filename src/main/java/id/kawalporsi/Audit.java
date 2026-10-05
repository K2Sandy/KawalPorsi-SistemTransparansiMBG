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

  static class DeviasiHargaStrategy implements AuditStrategy {
    public List<String> evaluate(TransaksiPengadaan c) {
      var messages = new ArrayList<String>();
      for (Line l : c.lines())
        if (exceeds(
            BigDecimal.valueOf(l.amount()), l.qty().multiply(BigDecimal.valueOf(l.reference())), c))
          messages.add(l.food() + " melewati ambang harga acuan per kg.");
      return messages;
    }
  }

  interface ObserverAudit {
    void flagged(Connection c, String id, String reason, boolean notify) throws SQLException;
  }

  static void log(Connection c, String id, String actor, String action, String note)
      throws SQLException {
    update(
        c,
        "insert into event(purchase,actor,action,note) values(?,?,?,?)",
        id,
        actor,
        action,
        note);
  }

  static final class AuditLogger implements ObserverAudit {
    public void flagged(Connection c, String id, String reason, boolean notify)
        throws SQLException {
      log(c, id, "AuditLogger", "Klaim dibekukan", reason);
    }
  }

  static final class FraudAlertSystem implements ObserverAudit {
    public void flagged(Connection c, String id, String reason, boolean notify)
        throws SQLException {
      if (notify)
        update(c, "insert into alert(purchase,message) values(?,?)", id, "Transaksi baru ditandai");
    }
  }

  private final List<ObserverAudit> observers = List.of(new AuditLogger(), new FraudAlertSystem());

  String save(
      Connection c,
      String kitchen,
      String actor,
      LocalDate date,
      String menu,
      int portions,
      List<Line> input,
      List<Receipt> files,
      boolean draft)
      throws SQLException {
    one(c, "select id from kitchen where id=? for update", kitchen);
    var prior =
        rows(c, "select id,state from purchase where kitchen=? and day_date=?", kitchen, date);
    if (!prior.isEmpty() && !prior.getFirst().str("state").equals("DRAFT"))
      throw new IllegalArgumentException("Catatan pada tanggal ini sudah dikirim.");
    var settings = one(c, "select * from rules where id=1");
    long isb =
        one(c, "select r.isb from kitchen k join region r on r.name=k.region where k.id=?", kitchen)
            .num("isb");
    if (portions < 1
        || portions > 1000000
        || menu.isBlank()
        || menu.length() > 240
        || input.isEmpty()
        || input.size() > 40)
      throw new IllegalArgumentException("Isi menu, 1–1.000.000 porsi, dan minimal satu bahan.");
    String id =
        prior.isEmpty()
            ? "TRX-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT)
            : prior.getFirst().str("id");
    var lines = new ArrayList<Line>();
    var labels = new ArrayList<String>();
    long total = 0;
    for (Line line : input) {
      if (line.qty().signum() <= 0
          || line.qty().scale() > 3
          || line.qty().compareTo(BigDecimal.valueOf(1000000)) > 0
          || line.amount() < 1
          || line.amount() > 100000000000L)
        throw new IllegalArgumentException("Periksa volume dan harga setiap bahan.");
      var f = BahanPangan.from(one(c, "select * from food where id=?", line.food()));
      labels.add(f.nama());
      lines.add(new Line(line.food(), line.qty(), line.amount(), f.hargaAcuanPerKg()));
      total = Math.addExact(total, line.amount());
    }
    var context =
        new TransaksiPengadaan(
            lines, portions, total, isb, settings.dec("tolerance"), settings.yes("low_flag"));
    boolean ratio = settings.str("strategy").equals("PORTION");
    boolean both = settings.yes("secondary");
    var findings = new ArrayList<String>();
    if (ratio || both) findings.addAll(new RasioPorsiStrategy().evaluate(context));
    if (!ratio || both) findings.addAll(new DeviasiHargaStrategy().evaluate(context));
    String state = draft ? "DRAFT" : findings.isEmpty() ? "APPROVED" : "FLAGGED";
    String strategy = both ? "Rasio porsi + harga satuan" : ratio ? "Rasio porsi" : "Harga satuan";
    update(
        c,
        "merge into purchase key(id) values(?,?,?,?,?,?,?,?,?,?,?)",
        id,
        kitchen,
        date,
        menu,
        portions,
        total,
        isb,
        context.tolerance(),
        strategy,
        state,
        String.join(" ", findings));
    update(c, "delete from item where purchase=?", id);
    for (int i = 0; i < lines.size(); i++) {
      var l = lines.get(i);
      update(
          c,
          "insert into item values(?,?,?,?,?,?,?)",
          id,
          i + 1,
          l.food(),
          labels.get(i),
          l.qty(),
          l.amount(),
          l.reference());
    }
    if (one(c, "select count(*) n from evidence where purchase=?", id).num("n") + files.size() > 12)
      throw new IllegalArgumentException("Maksimal 12 lampiran per transaksi.");
    for (var f : files) attach(c, id, f);
    log(c, id, actor, draft ? "Draf disimpan" : "Belanja dikirim", menu);
    if (!draft) {
      if (state.equals("FLAGGED"))
        for (ObserverAudit observer : observers)
          observer.flagged(c, id, String.join(" ", findings), settings.yes("notify"));
      else log(c, id, "AuditLogger", "Sesuai", "Transaksi lolos pemeriksaan otomatis.");
    }
    return id;
  }

  static void attach(Connection c, String id, Receipt f) throws SQLException {
    update(
        c,
        "insert into evidence(purchase,name,mime,contents) values(?,?,?,?)",
        id,
        f.name(),
        f.mime(),
        f.data());
  }
}
