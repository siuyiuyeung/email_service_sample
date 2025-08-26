# Email Service - S/MIME Support Design

## Overview

S/MIME (Secure/Multipurpose Internet Mail Extensions) provides end-to-end encryption and digital signatures for email messages. This design adds optional S/MIME support to the email service.

## Architecture Updates

```
┌─────────────────────────────────────────────────────────────┐
│                    Email Service Application                │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────────┐  ┌──────────────────┐  ┌────────────┐  │
│  │   REST API      │  │ Certificate Mgmt │  │  S/MIME    │  │
│  │  Controller     │  │   Controller     │  │  Config    │  │
│  └────────┬────────┘  └────────┬─────────┘  └─────┬──────┘  │
│           │                    │                   │        │
│  ┌────────▼────────────────────▼───────────────────▼──────┐ │
│  │                  Service Layer                         │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │ │
│  │  │ S/MIME Sign  │  │S/MIME Verify │  │ Certificate  │  │ │
│  │  │  & Encrypt   │  │  & Decrypt   │  │   Service    │  │ │
│  │  └──────────────┘  └──────────────┘  └──────────────┘  │ │
│  └─────────────────────────┬──────────────────────────────┘ │
│                            │                                │
│  ┌─────────────────────────▼──────────────────────────────┐ │
│  │               Certificate Storage Layer                │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │ │
│  │  │ Certificate  │  │  Trust Store │  │   Key Store  │  │ │
│  │  │  Repository  │  │   Manager    │  │   Manager    │  │ │
│  │  └──────────────┘  └──────────────┘  └──────────────┘  │ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

## Component Design

### 1. Enhanced Configuration

#### EmailProperties.java (Updated)
```java
@ConfigurationProperties(prefix = "email")
@Component
@Data
public class EmailProperties {
    private Smtp smtp = new Smtp();
    private Imap imap = new Imap();
    private General general = new General();
    private Smime smime = new Smime();
    
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
```

### 2. Certificate Management Models

#### Certificate.java
```java
@Entity
@Table(name = "certificates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Certificate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String emailAddress;
    
    @Column(unique = true, nullable = false)
    private String serialNumber;
    
    @Lob
    @Column(nullable = false)
    private byte[] certificateData;
    
    private String subjectDN;
    private String issuerDN;
    private LocalDateTime notBefore;
    private LocalDateTime notAfter;
    private String thumbprint;
    
    @Enumerated(EnumType.STRING)
    private CertificateType type;
    
    @Enumerated(EnumType.STRING)
    private CertificateStatus status;
    
    private boolean trusted = false;
    private LocalDateTime importedAt;
    private LocalDateTime lastUsedAt;
    
    @ElementCollection
    private Set<String> keyUsages = new HashSet<>();
    
    @ElementCollection
    private Set<String> extendedKeyUsages = new HashSet<>();
}

public enum CertificateType {
    SIGNING,
    ENCRYPTION,
    DUAL_USE
}

public enum CertificateStatus {
    ACTIVE,
    EXPIRED,
    REVOKED,
    PENDING_VALIDATION
}
```

#### CertificateRepository.java
```java
@Repository
public interface CertificateRepository extends JpaRepository<Certificate, Long> {
    Optional<Certificate> findByEmailAddressAndType(String emailAddress, CertificateType type);
    List<Certificate> findByEmailAddress(String emailAddress);
    List<Certificate> findByStatusAndNotAfterBefore(CertificateStatus status, LocalDateTime date);
    Optional<Certificate> findBySerialNumber(String serialNumber);
    Optional<Certificate> findByThumbprint(String thumbprint);
}
```

### 3. Enhanced Email Models

#### EmailMessage.java (Updated)
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "email_messages")
public class EmailMessage {
    // ... existing fields ...
    
    // S/MIME fields
    private boolean isSigned = false;
    private boolean isEncrypted = false;
    private boolean signatureValid;
    private String signerEmail;
    private String signerCertificateThumbprint;
    private LocalDateTime signatureTimestamp;
    
    @ElementCollection
    private List<String> encryptionRecipients = new ArrayList<>();
    
    @Enumerated(EnumType.STRING)
    private SmimeStatus smimeStatus;
    
    @Column(columnDefinition = "TEXT")
    private String smimeErrors;
    
    // Original message for S/MIME operations
    @Lob
    private byte[] originalMimeContent;
}

public enum SmimeStatus {
    NONE,
    SIGNED,
    ENCRYPTED,
    SIGNED_AND_ENCRYPTED,
    SIGNATURE_INVALID,
    DECRYPTION_FAILED,
    CERTIFICATE_EXPIRED,
    CERTIFICATE_UNTRUSTED
}
```

### 4. S/MIME Services

#### SmimeService.java
```java
@Service
@Slf4j
@RequiredArgsConstructor
public class SmimeService {
    private final EmailProperties properties;
    private final CertificateService certificateService;
    private final KeyStoreManager keyStoreManager;
    
    public MimeMessage signMessage(MimeMessage message, String signingAlias) throws Exception {
        if (!properties.getSmime().isEnabled()) {
            return message;
        }
        
        // Get signing certificate and private key
        PrivateKey privateKey = keyStoreManager.getPrivateKey(signingAlias);
        X509Certificate signingCert = keyStoreManager.getCertificate(signingAlias);
        
        // Create signed message
        SMIMESignedGenerator generator = new SMIMESignedGenerator();
        generator.addSignerInfoGenerator(
            new JcaSimpleSignerInfoGeneratorBuilder()
                .setProvider("BC")
                .build(properties.getSmime().getSignatureAlgorithm(), privateKey, signingCert)
        );
        
        // Add certificate chain
        List<X509Certificate> certChain = keyStoreManager.getCertificateChain(signingAlias);
        generator.addCertificates(new JcaCertStore(certChain));
        
        // Generate signed message
        MimeMultipart signedMultipart = generator.generate(message);
        MimeMessage signedMessage = new MimeMessage(message);
        signedMessage.setContent(signedMultipart);
        signedMessage.saveChanges();
        
        return signedMessage;
    }
    
    public MimeMessage encryptMessage(MimeMessage message, List<String> recipientEmails) throws Exception {
        if (!properties.getSmime().isEnabled()) {
            return message;
        }
        
        // Get recipient certificates
        List<X509Certificate> recipientCerts = new ArrayList<>();
        for (String email : recipientEmails) {
            X509Certificate cert = certificateService.getCertificateForEmail(email, CertificateType.ENCRYPTION);
            if (cert != null) {
                recipientCerts.add(cert);
            } else if (properties.getSmime().isRequireEncryption()) {
                throw new CertificateNotFoundException("No encryption certificate found for: " + email);
            }
        }
        
        if (recipientCerts.isEmpty()) {
            return message;
        }
        
        // Always encrypt to self if configured
        if (properties.getSmime().isAlwaysEncryptToSelf()) {
            X509Certificate selfCert = keyStoreManager.getCertificate(
                properties.getSmime().getDefaultSigningAlias()
            );
            if (selfCert != null) {
                recipientCerts.add(selfCert);
            }
        }
        
        // Create encrypted message
        SMIMEEnvelopedGenerator generator = new SMIMEEnvelopedGenerator();
        for (X509Certificate cert : recipientCerts) {
            generator.addRecipientInfoGenerator(
                new JceKeyTransRecipientInfoGenerator(cert).setProvider("BC")
            );
        }
        
        // Generate encrypted message
        MimeBodyPart encryptedPart = generator.generate(
            message,
            new JceCMSContentEncryptorBuilder(CMSAlgorithm.AES256_CBC).setProvider("BC").build()
        );
        
        MimeMessage encryptedMessage = new MimeMessage(message);
        encryptedMessage.setContent(encryptedPart.getContent(), encryptedPart.getContentType());
        encryptedMessage.saveChanges();
        
        return encryptedMessage;
    }
    
    public MimeMessage signAndEncryptMessage(MimeMessage message, String signingAlias, 
                                           List<String> recipientEmails) throws Exception {
        // First sign, then encrypt
        MimeMessage signedMessage = signMessage(message, signingAlias);
        return encryptMessage(signedMessage, recipientEmails);
    }
    
    public SmimeVerificationResult verifySignature(MimeMessage message) throws Exception {
        if (!isSmimeMessage(message)) {
            return SmimeVerificationResult.notSigned();
        }
        
        try {
            SMIMESigned signed = new SMIMESigned((MimeMultipart) message.getContent());
            Store<X509CertificateHolder> certs = signed.getCertificates();
            SignerInformationStore signers = signed.getSignerInfos();
            
            for (SignerInformation signer : signers) {
                Collection<X509CertificateHolder> certCollection = certs.getMatches(signer.getSID());
                X509CertificateHolder certHolder = certCollection.iterator().next();
                X509Certificate cert = new JcaX509CertificateConverter().getCertificate(certHolder);
                
                // Verify signature
                boolean valid = signer.verify(
                    new JcaSimpleSignerInfoVerifierBuilder().build(cert)
                );
                
                // Verify certificate
                CertificateValidationResult certValidation = certificateService.validateCertificate(cert);
                
                return SmimeVerificationResult.builder()
                    .signed(true)
                    .signatureValid(valid)
                    .signerEmail(extractEmailFromCertificate(cert))
                    .signerCertificate(cert)
                    .certificateValid(certValidation.isValid())
                    .certificateTrusted(certValidation.isTrusted())
                    .validationErrors(certValidation.getErrors())
                    .build();
            }
        } catch (Exception e) {
            log.error("Error verifying S/MIME signature", e);
            return SmimeVerificationResult.error(e.getMessage());
        }
        
        return SmimeVerificationResult.notSigned();
    }
    
    public MimeMessage decryptMessage(MimeMessage message) throws Exception {
        if (!isEncryptedMessage(message)) {
            return message;
        }
        
        try {
            SMIMEEnveloped enveloped = new SMIMEEnveloped(message);
            
            // Find recipient that matches our certificates
            PrivateKey decryptionKey = findDecryptionKey(enveloped);
            if (decryptionKey == null) {
                throw new DecryptionException("No suitable decryption key found");
            }
            
            // Decrypt
            RecipientInformationStore recipients = enveloped.getRecipientInfos();
            for (RecipientInformation recipient : recipients) {
                try {
                    byte[] decryptedContent = recipient.getContent(
                        new JceKeyTransEnvelopedRecipient(decryptionKey).setProvider("BC")
                    );
                    
                    // Create decrypted message
                    MimeMessage decryptedMessage = new MimeMessage(
                        message.getSession(),
                        new ByteArrayInputStream(decryptedContent)
                    );
                    
                    return decryptedMessage;
                } catch (Exception e) {
                    // Try next recipient
                    continue;
                }
            }
            
            throw new DecryptionException("Failed to decrypt message");
        } catch (Exception e) {
            log.error("Error decrypting S/MIME message", e);
            throw e;
        }
    }
    
    private boolean isSmimeMessage(MimeMessage message) throws Exception {
        String contentType = message.getContentType();
        return contentType != null && (
            contentType.contains("multipart/signed") ||
            contentType.contains("application/pkcs7-signature") ||
            contentType.contains("application/x-pkcs7-signature")
        );
    }
    
    private boolean isEncryptedMessage(MimeMessage message) throws Exception {
        String contentType = message.getContentType();
        return contentType != null && (
            contentType.contains("application/pkcs7-mime") ||
            contentType.contains("application/x-pkcs7-mime")
        );
    }
}
```

#### CertificateService.java
```java
@Service
@Slf4j
@RequiredArgsConstructor
public class CertificateService {
    private final CertificateRepository certificateRepository;
    private final TrustStoreManager trustStoreManager;
    private final EmailProperties properties;
    
    public X509Certificate getCertificateForEmail(String email, CertificateType type) {
        return certificateRepository.findByEmailAddressAndType(email, type)
            .map(cert -> convertToCertificate(cert.getCertificateData()))
            .orElse(null);
    }
    
    public Certificate importCertificate(byte[] certificateData, String email) throws Exception {
        X509Certificate x509Cert = convertToCertificate(certificateData);
        
        // Validate certificate
        CertificateValidationResult validation = validateCertificate(x509Cert);
        
        // Extract certificate details
        Certificate cert = Certificate.builder()
            .emailAddress(email)
            .serialNumber(x509Cert.getSerialNumber().toString())
            .certificateData(certificateData)
            .subjectDN(x509Cert.getSubjectDN().toString())
            .issuerDN(x509Cert.getIssuerDN().toString())
            .notBefore(convertToLocalDateTime(x509Cert.getNotBefore()))
            .notAfter(convertToLocalDateTime(x509Cert.getNotAfter()))
            .thumbprint(calculateThumbprint(x509Cert))
            .type(determineCertificateType(x509Cert))
            .status(validation.isValid() ? CertificateStatus.ACTIVE : CertificateStatus.PENDING_VALIDATION)
            .trusted(validation.isTrusted())
            .importedAt(LocalDateTime.now())
            .keyUsages(extractKeyUsages(x509Cert))
            .extendedKeyUsages(extractExtendedKeyUsages(x509Cert))
            .build();
        
        return certificateRepository.save(cert);
    }
    
    public CertificateValidationResult validateCertificate(X509Certificate certificate) {
        CertificateValidationResult result = new CertificateValidationResult();
        
        try {
            // Check expiration
            certificate.checkValidity();
            result.setValid(true);
        } catch (CertificateExpiredException e) {
            result.addError("Certificate has expired");
        } catch (CertificateNotYetValidException e) {
            result.addError("Certificate is not yet valid");
        }
        
        // Check trust
        if (trustStoreManager.isTrusted(certificate)) {
            result.setTrusted(true);
        } else {
            result.addError("Certificate is not trusted");
        }
        
        // Check key usage
        boolean[] keyUsage = certificate.getKeyUsage();
        if (keyUsage != null) {
            // Check for digital signature (position 0) or non-repudiation (position 1)
            if (!keyUsage[0] && !keyUsage[1]) {
                result.addError("Certificate cannot be used for signing");
            }
            // Check for key encipherment (position 2)
            if (!keyUsage[2]) {
                result.addError("Certificate cannot be used for encryption");
            }
        }
        
        return result;
    }
    
    @Scheduled(fixedDelay = 3600000) // Every hour
    public void cleanupExpiredCertificates() {
        LocalDateTime now = LocalDateTime.now();
        List<Certificate> expiredCerts = certificateRepository
            .findByStatusAndNotAfterBefore(CertificateStatus.ACTIVE, now);
        
        expiredCerts.forEach(cert -> {
            cert.setStatus(CertificateStatus.EXPIRED);
            certificateRepository.save(cert);
        });
    }
    
    private CertificateType determineCertificateType(X509Certificate cert) {
        boolean canSign = false;
        boolean canEncrypt = false;
        
        boolean[] keyUsage = cert.getKeyUsage();
        if (keyUsage != null) {
            canSign = keyUsage[0] || keyUsage[1]; // digitalSignature or nonRepudiation
            canEncrypt = keyUsage[2]; // keyEncipherment
        }
        
        if (canSign && canEncrypt) {
            return CertificateType.DUAL_USE;
        } else if (canSign) {
            return CertificateType.SIGNING;
        } else if (canEncrypt) {
            return CertificateType.ENCRYPTION;
        }
        
        return CertificateType.DUAL_USE; // Default
    }
}
```

### 5. Enhanced Email Services

#### EmailSenderService.java (Updated)
```java
@Service
@Slf4j
@RequiredArgsConstructor
public class EmailSenderService {
    private final JavaMailSender mailSender;
    private final EmailProperties properties;
    private final SmimeService smimeService;
    
    @Async
    public CompletableFuture<EmailMessage> sendEmail(EmailMessage emailMessage, SmimeOptions smimeOptions) {
        try {
            MimeMessage message = createMimeMessage(emailMessage);
            
            // Apply S/MIME if requested
            if (smimeOptions != null && properties.getSmime().isEnabled()) {
                message = applySmime(message, emailMessage, smimeOptions);
            }
            
            // Send email
            mailSender.send(message);
            
            // Update email status
            emailMessage.setStatus(EmailStatus.SENT);
            emailMessage.setSentDate(LocalDateTime.now());
            
            return CompletableFuture.completedFuture(emailMessage);
        } catch (Exception e) {
            log.error("Failed to send email", e);
            emailMessage.setStatus(EmailStatus.FAILED);
            emailMessage.setErrorMessage(e.getMessage());
            return CompletableFuture.completedFuture(emailMessage);
        }
    }
    
    private MimeMessage applySmime(MimeMessage message, EmailMessage emailMessage, 
                                  SmimeOptions options) throws Exception {
        MimeMessage processedMessage = message;
        
        // Store original content for reference
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        message.writeTo(baos);
        emailMessage.setOriginalMimeContent(baos.toByteArray());
        
        // Sign if requested
        if (options.isSign()) {
            String signingAlias = options.getSigningAlias() != null ? 
                options.getSigningAlias() : properties.getSmime().getDefaultSigningAlias();
            processedMessage = smimeService.signMessage(processedMessage, signingAlias);
            emailMessage.setSigned(true);
        }
        
        // Encrypt if requested
        if (options.isEncrypt()) {
            List<String> recipients = new ArrayList<>();
            recipients.addAll(emailMessage.getTo());
            if (emailMessage.getCc() != null) {
                recipients.addAll(emailMessage.getCc());
            }
            if (emailMessage.getBcc() != null) {
                recipients.addAll(emailMessage.getBcc());
            }
            
            processedMessage = smimeService.encryptMessage(processedMessage, recipients);
            emailMessage.setEncrypted(true);
            emailMessage.setEncryptionRecipients(recipients);
        }
        
        emailMessage.setSmimeStatus(determineSmimeStatus(
            options.isSign(), 
            options.isEncrypt()
        ));
        
        return processedMessage;
    }
}
```

#### EmailReceiverService.java (Updated)
```java
@Service
@Slf4j
@RequiredArgsConstructor
public class EmailReceiverService {
    private final Store imapStore;
    private final EmailProperties properties;
    private final EmailMessageRepository messageRepository;
    private final SmimeService smimeService;
    
    private EmailMessage processMessage(Message message, String folderName) {
        try {
            EmailMessage emailMessage = convertToEmailMessage(message);
            
            // Check for S/MIME
            if (properties.getSmime().isEnabled()) {
                processSmime(message, emailMessage);
            }
            
            return emailMessage;
        } catch (Exception e) {
            log.error("Error processing message", e);
            throw new RuntimeException(e);
        }
    }
    
    private void processSmime(Message message, EmailMessage emailMessage) {
        try {
            MimeMessage mimeMessage = (MimeMessage) message;
            
            // Store original MIME content
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            mimeMessage.writeTo(baos);
            emailMessage.setOriginalMimeContent(baos.toByteArray());
            
            // Check if message is encrypted
            if (smimeService.isEncryptedMessage(mimeMessage)) {
                emailMessage.setEncrypted(true);
                
                try {
                    // Decrypt message
                    MimeMessage decryptedMessage = smimeService.decryptMessage(mimeMessage);
                    
                    // Update email content with decrypted content
                    updateEmailContentFromMessage(emailMessage, decryptedMessage);
                    
                    // Check if decrypted message is signed
                    if (smimeService.isSmimeMessage(decryptedMessage)) {
                        verifySignature(decryptedMessage, emailMessage);
                    }
                } catch (Exception e) {
                    log.error("Failed to decrypt message", e);
                    emailMessage.setSmimeStatus(SmimeStatus.DECRYPTION_FAILED);
                    emailMessage.setSmimeErrors("Decryption failed: " + e.getMessage());
                }
            }
            // Check if message is signed
            else if (smimeService.isSmimeMessage(mimeMessage)) {
                verifySignature(mimeMessage, emailMessage);
            }
        } catch (Exception e) {
            log.error("Error processing S/MIME", e);
        }
    }
    
    private void verifySignature(MimeMessage message, EmailMessage emailMessage) {
        try {
            SmimeVerificationResult result = smimeService.verifySignature(message);
            
            emailMessage.setSigned(result.isSigned());
            emailMessage.setSignatureValid(result.isSignatureValid());
            emailMessage.setSignerEmail(result.getSignerEmail());
            
            if (result.getSignerCertificate() != null) {
                emailMessage.setSignerCertificateThumbprint(
                    calculateThumbprint(result.getSignerCertificate())
                );
            }
            
            // Update S/MIME status
            if (!result.isSignatureValid()) {
                emailMessage.setSmimeStatus(SmimeStatus.SIGNATURE_INVALID);
            } else if (!result.isCertificateValid()) {
                emailMessage.setSmimeStatus(SmimeStatus.CERTIFICATE_EXPIRED);
            } else if (!result.isCertificateTrusted()) {
                emailMessage.setSmimeStatus(SmimeStatus.CERTIFICATE_UNTRUSTED);
            } else {
                emailMessage.setSmimeStatus(
                    emailMessage.isEncrypted() ? 
                    SmimeStatus.SIGNED_AND_ENCRYPTED : 
                    SmimeStatus.SIGNED
                );
            }
            
            if (!result.getValidationErrors().isEmpty()) {
                emailMessage.setSmimeErrors(String.join(", ", result.getValidationErrors()));
            }
        } catch (Exception e) {
            log.error("Failed to verify signature", e);
            emailMessage.setSmimeStatus(SmimeStatus.SIGNATURE_INVALID);
            emailMessage.setSmimeErrors("Signature verification failed: " + e.getMessage());
        }
    }
}
```

### 6. REST API Updates

#### CertificateController.java
```java
@RestController
@RequestMapping("/api/v1/certificates")
@RequiredArgsConstructor
@Slf4j
public class CertificateController {
    private final CertificateService certificateService;
    
    @PostMapping("/import")
    public ResponseEntity<Certificate> importCertificate(
            @RequestParam("certificate") MultipartFile file,
            @RequestParam("email") String email) {
        try {
            Certificate cert = certificateService.importCertificate(
                file.getBytes(), email
            );
            return ResponseEntity.ok(cert);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    @GetMapping("/email/{email}")
    public ResponseEntity<List<Certificate>> getCertificatesForEmail(@PathVariable String email) {
        List<Certificate> certificates = certificateService.getCertificatesForEmail(email);
        return ResponseEntity.ok(certificates);
    }
    
    @PostMapping("/{id}/trust")
    public ResponseEntity<Certificate> trustCertificate(@PathVariable Long id) {
        Certificate cert = certificateService.trustCertificate(id);
        return ResponseEntity.ok(cert);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCertificate(@PathVariable Long id) {
        certificateService.deleteCertificate(id);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/my-certificates")
    public ResponseEntity<List<Certificate>> getMyCertificates() {
        List<Certificate> certificates = certificateService.getMyCertificates();
        return ResponseEntity.ok(certificates);
    }
}
```

#### EmailController.java (Updated)
```java
@PostMapping("/send")
public ResponseEntity<EmailResponse> sendEmail(
        @Valid @RequestBody EmailRequest request,
        @RequestParam(required = false) boolean sign,
        @RequestParam(required = false) boolean encrypt,
        @RequestParam(required = false) String signingAlias) {
    
    SmimeOptions smimeOptions = null;
    if (sign || encrypt) {
        smimeOptions = SmimeOptions.builder()
            .sign(sign)
            .encrypt(encrypt)
            .signingAlias(signingAlias)
            .build();
    }
    
    EmailMessage email = emailService.sendEmail(request, smimeOptions);
    return ResponseEntity.ok(new EmailResponse(email));
}

@GetMapping("/{messageId}/smime-info")
public ResponseEntity<SmimeInfo> getSmimeInfo(@PathVariable String messageId) {
    EmailMessage email = messageRepository.findById(messageId)
        .orElseThrow(() -> new EmailNotFoundException(messageId));
    
    SmimeInfo info = SmimeInfo.builder()
        .signed(email.isSigned())
        .encrypted(email.isEncrypted())
        .signatureValid(email.isSignatureValid())
        .signerEmail(email.getSignerEmail())
        .smimeStatus(email.getSmimeStatus())
        .errors(email.getSmimeErrors())
        .build();
    
    return ResponseEntity.ok(info);
}
```

### 7. DTOs and Supporting Classes

#### SmimeOptions.java
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmimeOptions {
    private boolean sign;
    private boolean encrypt;
    private String signingAlias;
    private List<String> additionalRecipients;
}
```

#### SmimeInfo.java
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmimeInfo {
    private boolean signed;
    private boolean encrypted;
    private boolean signatureValid;
    private String signerEmail;
    private SmimeStatus smimeStatus;
    private String errors;
    private CertificateInfo signerCertificate;
}
```

#### SmimeVerificationResult.java
```java
@Data
@Builder
public class SmimeVerificationResult {
    private boolean signed;
    private boolean signatureValid;
    private String signerEmail;
    private X509Certificate signerCertificate;
    private boolean certificateValid;
    private boolean certificateTrusted;
    private List<String> validationErrors;
    private String error;
    
    public static SmimeVerificationResult notSigned() {
        return SmimeVerificationResult.builder()
            .signed(false)
            .build();
    }
    
    public static SmimeVerificationResult error(String error) {
        return SmimeVerificationResult.builder()
            .signed(false)
            .error(error)
            .build();
    }
}
```

### 8. Configuration Updates

```properties
# S/MIME Configuration
email.smime.enabled=true
email.smime.key-store-path=${SMIME_KEYSTORE_PATH:./keystore.p12}
email.smime.key-store-password=${SMIME_KEYSTORE_PASSWORD}
email.smime.key-store-type=PKCS12
email.smime.trust-store-path=${SMIME_TRUSTSTORE_PATH:./truststore.jks}
email.smime.trust-store-password=${SMIME_TRUSTSTORE_PASSWORD}
email.smime.trust-store-type=JKS
email.smime.default-signing-alias=${SMIME_DEFAULT_ALIAS:default}
email.smime.signature-algorithm=SHA256withRSA
email.smime.encryption-algorithm=AES256_CBC
email.smime.always-encrypt-to-self=true
email.smime.require-encryption=false
email.smime.require-signature=false
email.smime.auto-download-certificates=true
email.smime.certificate-cache-duration=86400
```

### 9. Dependencies

```xml
<!-- Bouncy Castle for S/MIME -->
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcprov-jdk15on</artifactId>
    <version>1.70</version>
</dependency>
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcmail-jdk15on</artifactId>
    <version>1.70</version>
</dependency>
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcpkix-jdk15on</artifactId>
    <version>1.70</version>
</dependency>
```

### 10. Security Provider Registration

```java
@Configuration
public class SecurityConfiguration {
    
    @PostConstruct
    public void init() {
        // Register Bouncy Castle provider
        Security.addProvider(new BouncyCastleProvider());
    }
}
```

## Usage Examples

### Sending Signed Email
```bash
POST /api/v1/emails/send?sign=true
{
    "to": "recipient@example.com",
    "subject": "Signed Message",
    "textContent": "This is a digitally signed message"
}
```

### Sending Encrypted Email
```bash
POST /api/v1/emails/send?encrypt=true
{
    "to": "recipient@example.com",
    "subject": "Encrypted Message",
    "textContent": "This is an encrypted message"
}
```

### Sending Signed and Encrypted Email
```bash
POST /api/v1/emails/send?sign=true&encrypt=true&signingAlias=myalias
{
    "to": "recipient@example.com",
    "subject": "Secure Message",
    "textContent": "This is signed and encrypted"
}
```

### Importing a Certificate
```bash
POST /api/v1/certificates/import
Content-Type: multipart/form-data

certificate: [certificate file]
email: user@example.com
```

## Key Features

1. **Optional S/MIME Support**: Can be enabled/disabled via configuration
2. **Certificate Management**: Import, store, and manage certificates
3. **Automatic Certificate Validation**: Checks expiration and trust
4. **Flexible Encryption**: Encrypt to multiple recipients
5. **Signature Verification**: Automatic verification of incoming signed emails
6. **Transparent Decryption**: Automatic decryption of incoming encrypted emails
7. **Certificate Trust Store**: Manage trusted certificate authorities
8. **Key Store Management**: Secure storage of private keys
9. **API Support**: REST endpoints for S/MIME operations
10. **Backward Compatible**: Works seamlessly with non-S/MIME emails

## Testing Strategy

1. **Unit Tests**
   - Test certificate validation logic
   - Test S/MIME operations with test certificates
   - Mock Bouncy Castle operations

2. **Integration Tests**
   - Test full S/MIME flow with GreenMail
   - Test certificate import/export
   - Test trust store operations

3. **Security Tests**
   - Test invalid certificates
   - Test expired certificates
   - Test untrusted certificates
   - Test decryption failures