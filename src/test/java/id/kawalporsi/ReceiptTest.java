package id.kawalporsi;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ReceiptTest {
  @Test
  void keepsUtf8ReceiptNames() throws Exception {
    String body =
        "--receipt\r\nContent-Disposition: form-data; name=\"receipt\"; filename=\"Nota Café.pdf\""
            + "\r\nContent-Type: application/pdf\r\n\r\n%PDF-contoh\r\n--receipt--\r\n";
    var params =
        Web.Params.form(
            "multipart/form-data; boundary=receipt",
            new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    assertEquals(1, params.files.size());
    assertEquals("Nota Café.pdf", params.files.getFirst().name());
  }
}
