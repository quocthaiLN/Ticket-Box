package com.ticketbox.api.module.ticket.services;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TicketQrService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final String qrSecret;

    public TicketQrService(@Value("${app.ticket.qr-secret:}") String qrSecret) {
        this.qrSecret = qrSecret;
    }

    public IssuedQr issue(UUID ticketId) {
        String signature = sign(ticketId.toString());
        String content = ticketId + "." + signature;
        return new IssuedQr(
                "{\"ticket_id\":\"" + ticketId + "\"}",
                signature,
                sha256(content));
    }

    public String createPendingTokenHash() {
        return sha256(UUID.randomUUID().toString());
    }

    public boolean hasValidSignature(UUID ticketId, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        byte[] expected = sign(ticketId.toString()).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = signature.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }

    private String sign(String payload) {
        if (qrSecret == null || qrSecret.length() < 32) {
            throw new IllegalStateException("TICKET_QR_SECRET must be at least 32 characters long");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(qrSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to sign ticket QR", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    public record IssuedQr(String payload, String signature, String tokenHash) {
    }
}
