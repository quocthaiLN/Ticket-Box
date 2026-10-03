package com.ticketbox.api.module.artistbio.storage;

import com.ticketbox.api.module.artistbio.domain.exception.ArtistBioProcessingException;
import com.ticketbox.api.module.artistbio.domain.exception.ArtistBioStorageException;
import io.minio.*;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ArtistBioStorage {
    private final MinioClient client;
    private final String bucket;
    public ArtistBioStorage(MinioClient client, @Value("${spring.minio.bucket:ticket-box-storage}") String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    public String upload(UUID concertId, UUID objectId, byte[] bytes) {
        String key = "press-kits/" + concertId + "/" + objectId + ".pdf";
        try {
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(key)
                    .contentType("application/pdf")
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1).build());
            return "s3://" + bucket + "/" + key;
        } catch (Exception exception) {
            log.warn("Artist bio upload failed for object {} ({})", objectId, exception.getClass().getSimpleName());
            throw new ArtistBioStorageException();
        }
    }

    public byte[] download(String source) {
        String key = key(source);
        try (var input = client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            byte[] bytes = input.readNBytes(ArtistBioUpload.MAX_BYTES + 1);
            if (bytes.length > ArtistBioUpload.MAX_BYTES) throw new ArtistBioProcessingException("PDF exceeds 10 MiB", false);
            return bytes;
        } catch (ArtistBioProcessingException exception) {
            throw exception;
        } catch (io.minio.errors.ErrorResponseException exception) {
            boolean missing = "NoSuchKey".equals(exception.errorResponse().code());
            throw new ArtistBioProcessingException(missing ? "Source PDF is missing" : "Storage temporarily unavailable", !missing);
        } catch (Exception exception) {
            throw new ArtistBioProcessingException("Storage temporarily unavailable", true);
        }
    }

    public void delete(String source) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key(source)).build());
        } catch (Exception exception) {
            log.warn("Artist bio orphan cleanup failed for {} ({})", source, exception.getClass().getSimpleName());
        }
    }

    private String key(String source) {
        try {
            URI uri = URI.create(source);
            if (!"s3".equals(uri.getScheme()) || !bucket.equals(uri.getHost())
                    || uri.getQuery() != null || uri.getFragment() != null || uri.getUserInfo() != null
                    || uri.getPort() != -1 || !uri.getPath().matches("/press-kits/[0-9a-f-]{36}/[0-9a-f-]{36}\\.pdf")) {
                throw new IllegalArgumentException();
            }
            return uri.getPath().substring(1);
        } catch (RuntimeException exception) {
            throw new ArtistBioProcessingException("Invalid source PDF location", false);
        }
    }
}
