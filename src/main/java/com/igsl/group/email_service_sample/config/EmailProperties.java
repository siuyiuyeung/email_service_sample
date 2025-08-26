package com.igsl.group.email_service_sample.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "email")
@Component
@Data
public class EmailProperties {
    private Smtp smtp = new Smtp();
    private Imap imap = new Imap();
    private General general = new General();
    private Smime smime = new Smime();
    
    @Data
    public static class Smtp {
        private String host;
        private int port = 587;
        private String username;
        private String password;
        private boolean auth = true;
        private boolean starttls = true;
        private int connectionTimeout = 5000;
        private int timeout = 5000;
    }
    
    @Data
    public static class Imap {
        private String host;
        private int port = 993;
        private String username;
        private String password;
        private String folder = "INBOX";
        private boolean ssl = true;
        private int pollInterval = 60000; // 1 minute
    }
    
    @Data
    public static class General {
        private String fromAddress;
        private String fromName;
        private boolean debugEnabled = false;
        private int maxRetries = 3;
    }
    
    @Data
    public static class Smime {
        private boolean enabled = false;
        private String keyStorePath;
        private String keyStorePassword;
        private String keyStoreType = "PKCS12";
        private String trustStorePath;
        private String trustStorePassword;
        private String trustStoreType = "JKS";
        private String defaultSigningAlias;
        private String signatureAlgorithm = "SHA256withRSA";
        private String encryptionAlgorithm = "AES256_CBC";
        private boolean alwaysEncryptToSelf = true;
        private boolean requireEncryption = false;
        private boolean requireSignature = false;
        private boolean autoDownloadCertificates = true;
        private int certificateCacheDuration = 86400; // 24 hours
    }
}