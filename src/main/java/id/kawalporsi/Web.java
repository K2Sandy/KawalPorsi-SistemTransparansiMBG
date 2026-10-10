package id.kawalporsi;

import java.io.*;
import java.math.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

final class Web {
  static final Locale ID = Locale.forLanguageTag("id-ID");
  static final SecureRandom RANDOM = new SecureRandom();

  static String token() {
    byte[] bytes = new byte[28];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  static String h(Object x) {
    return Objects.toString(x, "")
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  static String url(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }

  static String rp(Number n) {
    return "Rp" + String.format(ID, "%,d", Math.round(n.doubleValue()));
  }

  static String num(Number n) {
    return String.format(ID, "%,d", n.longValue());
  }

  static String date(LocalDate d) {
    return d.format(DateTimeFormatter.ofPattern("d MMM uuuu", ID));
  }

  static String pct(double n) {
    return String.format(ID, "%+.1f%%", n);
  }

  static String icon(String file) {
    return "<img alt='' class='icon' src='/icons/" + file + ".svg'>";
  }

  static String a(String page, String label, String cls) {
    return "<a router-ignore class='" + cls + "' href='/?v=" + page + "'>" + label + "</a>";
  }

  static String field(String label, String control) {
    return "<label>" + h(label) + control + "</label>";
  }

  static String input(String name, String value, String type, String attrs) {
    return "<input name='" + name + "' type='" + type + "' value='" + h(value) + "' " + attrs + ">";
  }

  static String option(String value, String label, String current) {
    return "<option value='"
        + h(value)
        + "' "
        + (value.equals(current) ? "selected" : "")
        + ">"
        + h(label)
        + "</option>";
  }

  static String paper(String heading, String inner) {
    return "<section class='paper'><h2>" + h(heading) + "</h2>" + inner + "</section>";
  }

  static String title(String heading, String lead) {
    return "<header class='title'><h1>" + h(heading) + "</h1><p>" + h(lead) + "</p></header>";
  }

  static String table(String heads, String body) {
    StringBuilder s = new StringBuilder("<div class='scroll'><table><thead><tr>");
    for (String v : heads.split("\\|")) s.append("<th>").append(h(v)).append("</th>");
    return s.append("</tr></thead><tbody>")
        .append(body.isEmpty() ? "<tr><td colspan='9'>Belum ada data yang cocok.</td></tr>" : body)
        .append("</tbody></table></div>")
        .toString();
  }

  static String tr(String... cells) {
    StringBuilder s = new StringBuilder("<tr>");
    for (String cell : cells) s.append("<td>").append(cell).append("</td>");
    return s.append("</tr>").toString();
  }

  static String badge(String state) {
    String label =
        switch (state) {
          case "DRAFT" -> "Draf";
          case "APPROVED" -> "Sesuai";
          case "CLEARED" -> "Disetujui auditor";
          case "FLAGGED" -> "Ditandai";
          case "WAITING" -> "Menunggu klarifikasi";
          case "REVIEW" -> "Ditinjau";
          case "REJECTED" -> "Tidak wajar";
          default -> "Sedang diverifikasi";
        };
    String img =
        switch (state) {
          case "APPROVED", "CLEARED" -> "7616b";
          case "REJECTED", "FLAGGED" -> "626d3";
          case "DRAFT" -> "f0db0";
          default -> "e11a5";
        };
    return "<span class='badge " + state + "'>" + icon(img) + label + "</span>";
  }

  static boolean released(String state) {
    return state.equals("APPROVED") || state.equals("CLEARED");
  }

  static boolean open(String state) {
    return List.of("FLAGGED", "WAITING", "REVIEW").contains(state);
  }

  static String statusPublic(String state) {
    return open(state) ? "PENDING" : state;
  }
}
