package com.ticketbox.api.module.artistbio.storage;

import com.ticketbox.api.infrastructure.exception.AppException;
import com.ticketbox.api.module.shared.validation.RequestValidationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

public final class ArtistBioUpload {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    private ArtistBioUpload() {}

    public static byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty()) throw invalid();
        if (file.getSize() > MAX_BYTES) throw tooLarge();
        if (!"application/pdf".equalsIgnoreCase(file.getContentType())) throw invalid();
        try (var input = file.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw tooLarge();
            if (bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) throw invalid();
            return bytes;
        } catch (IOException exception) {
            throw invalid();
        }
    }

    private static RequestValidationException invalid() {
        return new RequestValidationException("INVALID_UPLOAD", "A non-empty PDF file is required");
    }
    private static AppException tooLarge() {
        return new AppException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "PDF exceeds 10 MiB");
    }
}
