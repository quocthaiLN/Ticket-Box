package com.ticketbox.api.module.artistbio.ai;

import com.ticketbox.api.module.artistbio.domain.exception.ArtistBioProcessingException;
import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.encryption.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PdfTextExtractorTest {
    private final PdfTextExtractor extractor = new PdfTextExtractor();

    @Test void extractsAndNormalizesText() throws Exception {
        assertThat(extractor.extract(pdf(1, true, false))).isEqualTo("Artist biography and achievements.");
    }
    @Test void rejectsScansCorruptEncryptedAndOversizedDocuments() throws Exception {
        for (byte[] bytes : new byte[][] {pdf(1, false, false), pdf(1, true, true), pdf(51, true, false), new byte[] {1, 2}}) {
            assertThatThrownBy(() -> extractor.extract(bytes)).isInstanceOf(ArtistBioProcessingException.class)
                    .extracting("retryable").isEqualTo(false);
        }
    }
    @Test void boundsDenseText() throws Exception {
        try (var doc = new PDDocument(); var output = new ByteArrayOutputStream()) {
            var page = new PDPage(); doc.addPage(page);
            try (var content = new PDPageContentStream(doc, page)) {
                content.beginText(); content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                for (int i = 0; i < 1000; i++) { content.showText("Artist biography and achievements. "); }
                content.endText();
            }
            doc.save(output);
            assertThat(extractor.extract(output.toByteArray())).hasSizeLessThanOrEqualTo(30_000);
        }
    }
    private byte[] pdf(int pages, boolean text, boolean encrypted) throws Exception {
        try (var doc = new PDDocument(); var output = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) doc.addPage(new PDPage());
            if (text) {
                try (var content = new PDPageContentStream(doc, doc.getPage(0))) {
                    content.beginText(); content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.showText("Artist biography and achievements."); content.endText();
                }
            }
            if (encrypted) doc.protect(new StandardProtectionPolicy("owner", "password", new AccessPermission()));
            doc.save(output); return output.toByteArray();
        }
    }
}
