package com.ticketbox.api.module.artistbio.storage;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;

class ArtistBioUploadTest {
    @Test void acceptsPdfSignature() {
        assertThat(ArtistBioUpload.read(new MockMultipartFile("file", "kit.pdf", "application/pdf", "%PDF-1.7".getBytes())))
                .startsWith("%PDF-".getBytes());
    }
    @Test void rejectsEmptyWrongTypeWrongSignatureAndOversize() {
        for (var file : new MockMultipartFile[] {
                new MockMultipartFile("file", new byte[0]),
                new MockMultipartFile("file", "x.pdf", "text/plain", "%PDF-1.7".getBytes()),
                new MockMultipartFile("file", "x.pdf", "application/pdf", "not-pdf".getBytes()),
                new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[ArtistBioUpload.MAX_BYTES + 1])}) {
            assertThatThrownBy(() -> ArtistBioUpload.read(file)).isInstanceOf(RuntimeException.class);
        }
    }
}
