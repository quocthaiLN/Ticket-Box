package com.ticketbox.api.infrastructure.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinIOConfig {

    @Value("${spring.minio.host:localhost}")
    private String host;

    @Value("${spring.minio.port:9000}")
    private int port;

    @Value("${spring.minio.username:admin}")
    private String accessKey;

    @Value("${spring.minio.password:admin123456}")
    private String secretKey;

    @Value("${spring.minio.secure:false}")
    private boolean secure;

    @Bean
    public MinioClient minioClient() {
        String endpoint;
        if (host.startsWith("http://") || host.startsWith("https://")) {
            endpoint = host;
        } else {
            String protocol = secure ? "https" : "http";
            endpoint = String.format("%s://%s:%d", protocol, host, port);
        }

        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}

