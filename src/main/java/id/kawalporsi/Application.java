package id.kawalporsi;

import static id.kawalporsi.Database.*;
import static id.kawalporsi.Service.*;
import static id.kawalporsi.Web.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Request handling for Vaadin views, form submissions and authorized downloads. */
public final class Application {
  final Service service;

  public Application(String url) {
    Database db = new Database(url);
    db.initialize();
    service = new Service(db);
  }

  record Response(int code, String type, byte[] data, Map<String, String> headers) {
    static Response text(int code, String type, String body) {
      return new Response(code, type, body.getBytes(StandardCharsets.UTF_8), Map.of());
    }
  }

  static String document(String body) {
    return "<!doctype html><html lang='id'><head><meta charset='utf-8'><meta name='viewport'"
               + " content='width=device-width,initial-scale=1'><title>KawalPorsi · Transparansi"
               + " Dapur MBG</title><link rel='stylesheet' href='/styles.css'><script defer"
               + " src='/ui.js'></script></head><body>"
        + body
        + "</body></html>";
  }

  String fragment(Session s, Params q) {
    return service.db.transaction(c -> new Pages(c, s, q).render());
  }

  static String errorBody(String msg) {
    return "<main class='error'>"
        + Pages.logo()
        + "<h1>Permintaan belum selesai</h1><p role='alert'>"
        + h(msg)
        + "</p><button type='button' class='btn' data-back>Kembali ke formulir</button> "
        + Pages.linkBtn("login", "Halaman masuk")
        + "</main>";
  }

  Response handle(
      String method, String path, String query, String contentType, InputStream input, Session s) {
    try {
      Params q = Params.query(query);
      if (method.equals("POST") && path.equals("/action")) {
        String dest = service.action(s, Params.form(contentType, input));
        return new Response(303, "text/plain", new byte[0], Map.of("Location", "/?v=" + dest));
      }
      if (method.equals("GET") && path.equals("/file"))
        return service.db.transaction(
            c -> {
              var file =
                  one(
                      c,
                      "select purchase,name,mime,contents from evidence where id=?",
                      UUID.fromString(q.get("id")));
              record(c, user(c, s), file.str("purchase"), false);
              var blob = (java.sql.Blob) file.get("contents");
              return new Response(
                  200,
                  file.str("mime"),
                  blob.getBytes(1, (int) blob.length()),
                  Map.of(
                      "Content-Disposition",
                      "attachment; filename*=UTF-8''" + url(file.str("name")).replace("+", "%20")));
            });
      if (method.equals("GET") && path.equals("/export"))
        return service.db.transaction(
            c -> {
              var b =
                  new StringBuilder("\ufeffTanggal,Dapur,Wilayah,Porsi,Biaya per porsi,Status\r\n");
              for (Row r :
                  rows(c, PURCHASE + " where t.state<>'DRAFT' order by t.day_date desc,k.name")) {
                String state = r.str("state");
                List<String> cells =
                    List.of(
                        r.str("day_date"),
                        r.str("kitchen_name"),
                        r.str("region"),
                        r.str("portions"),
                        released(state) ? String.valueOf(Math.round(Pages.cost(r))) : "",
                        open(state)
                            ? "Sedang diverifikasi"
                            : state.equals("REJECTED")
                                ? "Tidak wajar"
                                : state.equals("CLEARED") ? "Disetujui auditor" : "Sesuai");
                b.append(
                        cells.stream()
                            .map(Application::csv)
                            .collect(java.util.stream.Collectors.joining(",")))
                    .append("\r\n");
              }
              return new Response(
                  200,
                  "text/csv; charset=utf-8",
                  b.toString().getBytes(StandardCharsets.UTF_8),
                  Map.of("Content-Disposition", "attachment; filename=kawalporsi-publik.csv"));
            });
      if (method.equals("GET")
          && path.matches(
              "/(styles\\.css|ui\\.js|icons/[A-Za-z0-9-]+\\.svg|fonts/[a-z0-9-]+\\.ttf)")) {
        try (var resource = getClass().getResourceAsStream("/META-INF/resources" + path)) {
          if (resource != null) {
            String mime =
                path.endsWith(".css")
                    ? "text/css"
                    : path.endsWith(".js")
                        ? "text/javascript"
                        : path.endsWith(".ttf") ? "font/ttf" : "image/svg+xml";
            return new Response(200, mime, resource.readAllBytes(), Map.of());
          }
        }
      }
      return Response.text(
          404, "text/html; charset=utf-8", document(errorBody("Halaman tidak ditemukan.")));
    } catch (SecurityException e) {
      return Response.text(403, "text/html; charset=utf-8", document(errorBody(e.getMessage())));
    } catch (IllegalArgumentException e) {
      return Response.text(400, "text/html; charset=utf-8", document(errorBody(e.getMessage())));
    } catch (Exception e) {
      e.printStackTrace();
      return Response.text(
          500,
          "text/html; charset=utf-8",
          document(errorBody("Perubahan belum disimpan. Silakan coba lagi.")));
    }
  }

  private static String csv(String v) {
    if (v.matches("^[=+@\\-].*")) v = "'" + v;
    return "\"" + v.replace("\"", "\"\"") + "\"";
  }
}
