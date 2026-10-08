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
}
