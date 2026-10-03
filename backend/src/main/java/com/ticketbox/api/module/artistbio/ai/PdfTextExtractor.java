package com.ticketbox.api.module.artistbio.ai;

import com.ticketbox.api.module.artistbio.domain.exception.ArtistBioProcessingException;
import java.io.IOException;
import java.io.Writer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class PdfTextExtractor {
    public static final int MAX_CONTEXT = 30_000;

    public String extract(byte[] pdf) {
        try (var document = Loader.loadPDF(pdf)) {
            if (document.isEncrypted()) throw invalid("Encrypted PDFs are not supported");
            if (document.getNumberOfPages() > 50) throw invalid("PDF exceeds 50 pages");
            // Bound the extracted buffer even for very dense documents.
            StringBuilder buffer = new StringBuilder(MAX_CONTEXT);
            Writer writer = new Writer() {
                @Override public void write(char[] chars, int offset, int length) {
                    int remaining = MAX_CONTEXT - buffer.length();
                    if (remaining > 0) buffer.append(chars, offset, Math.min(length, remaining));
                }
                @Override public void flush() {}
                @Override public void close() {}
            };
            new PDFTextStripper().writeText(document, writer);
            String text = buffer.toString().replaceAll("(?m)^\\s*\\d+\\s*$", " ")
                    .replaceAll("[\\p{Cc}&&[^\\n\\t]]", " ").replaceAll("(?U)\\s+", " ").trim();
            if (text.isBlank()) throw invalid("PDF contains no extractable text; OCR is not supported");
            return text;
        } catch (IOException | IllegalArgumentException exception) {
            throw invalid("PDF is corrupt or encrypted");
        }
    }

    private ArtistBioProcessingException invalid(String message) {
        return new ArtistBioProcessingException(message, false);
    }
}
