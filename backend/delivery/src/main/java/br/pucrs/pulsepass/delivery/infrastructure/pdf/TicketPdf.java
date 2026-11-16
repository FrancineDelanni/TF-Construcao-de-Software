package br.pucrs.pulsepass.delivery.infrastructure.pdf;

import br.pucrs.pulsepass.delivery.domain.TicketSigner;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.ByteArrayOutputStream;
import java.text.Normalizer;
import java.util.Map;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.springframework.stereotype.Component;

@Component
public class TicketPdf {
  private final TicketSigner signer;
  private final ObjectMapper json;

  public TicketPdf(TicketSigner signer, ObjectMapper json) {
    this.signer = signer;
    this.json = json;
  }

  public byte[] render(Map<String, Object> ticket) throws Exception {
    String content =
        json.writeValueAsString(
            Map.of(
                "ticketId",
                ticket.get("id").toString(),
                "orderItemId",
                ticket.get("order_item_id").toString(),
                "eventId",
                ticket.get("event_id").toString(),
                "seatId",
                ticket.get("seat_id").toString(),
                "issuedAt",
                ticket.get("issued_at").toString(),
                "keyId",
                "delivery-ed25519-v1"));
    String signed = signer.sign(content);
    var matrix = new QRCodeWriter().encode(signed, BarcodeFormat.QR_CODE, 600, 600);
    try (var doc = new PDDocument();
        var buffer = new ByteArrayOutputStream()) {
      var page = new PDPage(PDRectangle.A4);
      doc.addPage(page);
      try (var stream = new PDPageContentStream(doc, page)) {
        stream.setNonStrokingColor(0.08f, 0.14f, 0.24f);
        stream.addRect(0, 730, 595, 112);
        stream.fill();
        text(stream, 40, 785, 26, "PulsePass", true, 1f);
        text(stream, 40, 755, 11, "INGRESSO - DEMONSTRACAO / SEM VALOR COMERCIAL", false, 1f);
        text(stream, 40, 685, 20, ticket.get("event_name").toString(), true, 0.12f);
        text(stream, 40, 650, 12, "Data: " + ticket.get("event_starts_at"), false, 0.12f);
        text(stream, 40, 625, 12, "Local: " + ticket.get("venue"), false, 0.12f);
        text(stream, 40, 600, 14, "Assento: " + ticket.get("seat_description"), true, 0.12f);
        stream.drawImage(
            LosslessFactory.createFromImage(doc, MatrixToImageWriter.toBufferedImage(matrix)),
            145,
            225,
            305,
            305);
        text(stream, 40, 180, 10, "Ingresso: " + ticket.get("id"), false, 0.12f);
        text(stream, 40, 160, 10, "Pedido: " + ticket.get("order_id"), false, 0.12f);
        text(stream, 40, 140, 10, "Emissao: " + ticket.get("issued_at"), false, 0.12f);
        text(stream, 40, 85, 10, "QR assinado com Ed25519. Preserve este documento.", false, 0.12f);
        text(
            stream,
            40,
            65,
            9,
            "Validacao de entrada e check-in ainda fora deste incremento.",
            false,
            0.12f);
      }
      doc.save(buffer);
      return buffer.toByteArray();
    }
  }

  private static void text(
      PDPageContentStream stream,
      float x,
      float y,
      float size,
      String value,
      boolean bold,
      float color)
      throws Exception {
    // Seed/demo text uses latin glyphs; normalize controls and wrap long labels to the page width.
    String normalized =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replaceAll("[^\\x20-\\x7E]", "?");
    var font =
        new PDType1Font(
            bold ? Standard14Fonts.FontName.HELVETICA_BOLD : Standard14Fonts.FontName.HELVETICA);
    while (font.getStringWidth(normalized) / 1000 * size > 515 && size > 7) size -= 0.5f;
    stream.beginText();
    stream.setNonStrokingColor(color);
    stream.setFont(font, size);
    stream.newLineAtOffset(x, y);
    stream.showText(normalized);
    stream.endText();
  }
}
