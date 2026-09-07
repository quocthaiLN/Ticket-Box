package com.ticketbox.api.module.shared.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Service
@AllArgsConstructor
@NoArgsConstructor
public class StorageService {

    @Value("${app.storage.base-url:http://localhost:9000/ticket-box-storage}")
    private String baseUrl;

    public String buildPublicUrl(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return relativePath;
        }

        if (relativePath.startsWith("http://") || relativePath.startsWith("https://")) {
            return relativePath;
        }

        String base = (baseUrl != null && baseUrl.endsWith("/"))
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : (baseUrl != null ? baseUrl : "");

        String path = relativePath.startsWith("/")
                ? relativePath.substring(1)
                : relativePath;

        return base.isEmpty() ? path : base + "/" + path;
    }
}
