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

  static class Params {
    final Map<String, List<String>> values = new LinkedHashMap<>();
    final List<Audit.Receipt> files = new ArrayList<>();

    void add(String k, String v) {
      values.computeIfAbsent(k, key -> new ArrayList<>()).add(v);
    }

    String get(String k) {
      return values.getOrDefault(k, List.of("")).getFirst().strip();
    }

    String required(String k, int max) {
      String v = get(k);
      if (v.isBlank() || v.length() > max)
        throw new IllegalArgumentException(
            "Lengkapi kolom " + k + " (maks. " + max + " karakter).");
      return v;
    }

    long positive(String key, long max) {
      try {
        long x = Long.parseLong(get(key));
        if (x > 0 && x <= max) return x;
      } catch (NumberFormatException ignored) {
      }
      throw new IllegalArgumentException("Nilai " + key + " tidak valid.");
    }

    boolean on(String k) {
      return values.containsKey(k);
    }

    static Params query(String query) {
      Params p = new Params();
      if (query != null)
        for (String item : query.split("&")) {
          String[] kv = item.split("=", 2);
          if (kv.length == 2)
            p.add(
                URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
      return p;
    }

    static Params form(String type, InputStream stream) throws IOException {
      byte[] bytes = stream.readNBytes(24 * 1024 * 1024 + 1);
      if (bytes.length > 24 * 1024 * 1024)
        throw new IllegalArgumentException("Ukuran kiriman melebihi 24 MB.");
      if (type == null || !type.startsWith("multipart/form-data"))
        return query(new String(bytes, StandardCharsets.UTF_8));
      var matcher = java.util.regex.Pattern.compile("boundary=\"?([^\";]+)").matcher(type);
      if (!matcher.find()) throw new IllegalArgumentException("Format lampiran tidak valid.");
      String delim = "--" + matcher.group(1);
      String raw = new String(bytes, StandardCharsets.ISO_8859_1);
      Params p = new Params();
      int start = raw.indexOf(delim) + delim.length();
      while (start >= delim.length() && raw.startsWith("\r\n", start)) {
        int headerEnd = raw.indexOf("\r\n\r\n", start),
            end = raw.indexOf("\r\n" + delim, headerEnd + 4);
        if (headerEnd < 0 || end < 0)
          throw new IllegalArgumentException("Data unggahan tidak lengkap.");
        String header = raw.substring(start, headerEnd);
        var name = java.util.regex.Pattern.compile("(?:^|; )name=\"([^\"]+)\"").matcher(header);
        var file = java.util.regex.Pattern.compile("filename=\"([^\"]*)\"").matcher(header);
        byte[] data = raw.substring(headerEnd + 4, end).getBytes(StandardCharsets.ISO_8859_1);
        if (name.find()) {
          if (file.find()) {
            if (!file.group(1).isEmpty()) {
              String filename =
                  new String(file.group(1).getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
              p.files.add(receipt(filename, data));
            }
          } else p.add(name.group(1), new String(data, StandardCharsets.UTF_8));
        }
        start = end + 2 + delim.length();
      }
      return p;
    }

    static Audit.Receipt receipt(String filename, byte[] data) {
      if (data.length < 5 || data.length > 10 * 1024 * 1024)
        throw new IllegalArgumentException("Nota harus berukuran 1 byte–10 MB.");
      String mime;
      if (data[0] == (byte) 0xff && data[1] == (byte) 0xd8 && data[2] == (byte) 0xff)
        mime = "image/jpeg";
      else if (data.length >= 8
          && java.util.Arrays.equals(
              Arrays.copyOf(data, 8), new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10}))
        mime = "image/png";
      else if (new String(data, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-"))
        mime = "application/pdf";
      else throw new IllegalArgumentException("Pilih nota JPG, PNG, atau PDF.");
      String safe = filename.replaceAll("[^\\p{L}\\p{N}._ -]", "_");
      return new Audit.Receipt(safe.substring(0, Math.min(safe.length(), 130)), mime, data);
    }
  }
}
