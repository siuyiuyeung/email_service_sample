package com.igsl.group.email_service_sample.service;

import com.igsl.group.email_service_sample.config.EmailProperties;
import com.igsl.group.email_service_sample.exception.CertificateNotFoundException;
import com.igsl.group.email_service_sample.exception.DecryptionException;
import com.igsl.group.email_service_sample.model.Certificate;
import com.igsl.group.email_service_sample.model.CertificateStatus;
import com.igsl.group.email_service_sample.model.CertificateType;
import com.igsl.group.email_service_sample.model.SmimeStatus;
import com.igsl.group.email_service_sample.repository.CertificateRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.smime.SMIMECapabilitiesAttribute;
import org.bouncycastle.asn1.smime.SMIMECapability;
import org.bouncycastle.asn1.smime.SMIMECapabilityVector;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSAlgorithm;
import org.bouncycastle.cms.RecipientInformation;
import org.bouncycastle.cms.RecipientInformationStore;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.bouncycastle.cms.jcajce.JceKeyTransEnvelopedRecipient;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.bouncycastle.mail.smime.SMIMEEnveloped;
import org.bouncycastle.mail.smime.SMIMEEnvelopedGenerator;
import org.bouncycastle.mail.smime.SMIMESigned;
import org.bouncycastle.mail.smime.SMIMESignedGenerator;
import org.bouncycastle.mail.smime.SMIMEUtil;
import org.bouncycastle.util.Store;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.mail.Header;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class SMimeService {

    private final EmailProperties properties;
    private final CertificateRepository certificateRepository;

    private PrivateKey privateKey;
    private X509Certificate ownCertificate;

    // -------------------------------------------------------------------------
    // Initialization
    // -------------------------------------------------------------------------

    @PostConstruct
    public void init() {
        if (!properties.getSmime().isEnabled()) {
            log.info("S/MIME is disabled – skipping initialization");
            return;
        }
        try {
            loadKeyStore();
            log.info("S/MIME service initialized successfully");
        } catch (Exception e) {
            log.warn("S/MIME service initialization failed: {}. S/MIME operations will be unavailable.",
                    e.getMessage());
        }
    }

    // Called from @PostConstruct – no Spring transaction context available here,
    // so repository operations run with individual auto-commit semantics (fine for one-time import).
    private void loadKeyStore() throws Exception {
        String keyStorePath = properties.getSmime().getKeyStorePath();
        String keyStorePassword = properties.getSmime().getKeyStorePassword();
        String keyStoreType = properties.getSmime().getKeyStoreType();
        String signingAlias = properties.getSmime().getDefaultSigningAlias();

        if (keyStorePath == null || keyStorePath.isEmpty()) {
            log.warn("S/MIME keystore path not configured");
            return;
        }

        KeyStore keyStore = KeyStore.getInstance(keyStoreType);
        char[] password = keyStorePassword != null ? keyStorePassword.toCharArray() : new char[0];

        try (InputStream is = Files.newInputStream(Paths.get(keyStorePath))) {
            keyStore.load(is, password);
        }

        // Auto-discover alias if not configured
        if (signingAlias == null || signingAlias.isEmpty()) {
            Enumeration<String> aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                String a = aliases.nextElement();
                if (keyStore.isKeyEntry(a)) {
                    signingAlias = a;
                    break;
                }
            }
        }

        if (signingAlias == null) {
            throw new IllegalStateException("No private key entry found in S/MIME keystore");
        }

        privateKey = (PrivateKey) keyStore.getKey(signingAlias, password);
        ownCertificate = (X509Certificate) keyStore.getCertificate(signingAlias);

        log.info("Loaded S/MIME signing key for: {}", ownCertificate.getSubjectX500Principal().getName());
        importOwnCertificate(ownCertificate);
    }

    // -------------------------------------------------------------------------
    // Certificate management
    // -------------------------------------------------------------------------

    // No @Transactional – called from @PostConstruct (no Spring tx context).
    // Each repository call uses its own auto-commit transaction, which is fine here.
    private void importOwnCertificate(X509Certificate cert) {
        try {
            String thumbprint = computeThumbprint(cert);
            if (certificateRepository.findByThumbprint(thumbprint).isPresent()) {
                return;
            }
            Certificate entity = buildCertificateEntity(cert, CertificateType.DUAL_USE, true);
            certificateRepository.save(entity);
            log.info("Imported own S/MIME certificate: {}", cert.getSubjectX500Principal().getName());
        } catch (Exception e) {
            log.warn("Could not persist own S/MIME certificate: {}", e.getMessage());
        }
    }

    /**
     * Import a DER- or PEM-encoded X.509 certificate for the given email address.
     */
    @Transactional
    public Certificate importCertificate(byte[] certData, String emailAddress) throws Exception {
        X509Certificate x509 = parseCertificate(certData);
        return persistCertificate(x509, emailAddress, CertificateType.ENCRYPTION, false);
    }

    /**
     * Import a PEM-encoded certificate.
     */
    @Transactional
    public Certificate importCertificateFromPem(String pem, String emailAddress) throws Exception {
        byte[] der = decodePem(pem);
        return importCertificate(der, emailAddress);
    }

    /**
     * Extract and persist the signer certificate from a signed message.
     * Returns the signer's email address, or null if not found.
     * Participates in the caller's transaction (no own @Transactional to avoid self-invocation issues).
     */
    @SuppressWarnings("unchecked") // BC Store.getMatches() uses raw generic type
    public String discoverCertificateFromEmail(MimeMessage message) {
        if (!isSigned(message)) {
            return null;
        }
        try {
            SMIMESigned smimeSigned = buildSMIMESigned(message);
            if (smimeSigned == null) {
                return null;
            }
            Store<X509CertificateHolder> certs = smimeSigned.getCertificates();
            for (SignerInformation signer : smimeSigned.getSignerInfos().getSigners()) {
                Collection<X509CertificateHolder> matches = certs.getMatches(signer.getSID());
                if (matches.isEmpty()) {
                    continue;
                }
                X509CertificateHolder holder = matches.iterator().next();
                X509Certificate x509 = new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder);
                String signerEmail = extractEmailFromCertificate(x509);
                if (signerEmail != null) {
                    persistCertificate(x509, signerEmail, CertificateType.SIGNING, false);
                    log.debug("Discovered S/MIME certificate for: {}", signerEmail);
                    return signerEmail;
                }
            }
        } catch (Exception e) {
            log.debug("Could not discover certificate from signed email: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Validate certificate statuses and mark expired ones in the database.
     */
    @Transactional
    public void validateCertificateStatuses() {
        List<Certificate> candidates = certificateRepository
                .findByStatusAndNotAfterBefore(CertificateStatus.ACTIVE, LocalDateTime.now());
        for (Certificate cert : candidates) {
            cert.setStatus(CertificateStatus.EXPIRED);
            certificateRepository.save(cert);
            log.info("Certificate for {} has expired – marked accordingly", cert.getEmailAddress());
        }
    }

    /**
     * Return all persisted certificates.
     */
    public List<Certificate> getAllCertificates() {
        return certificateRepository.findAll();
    }

    /**
     * Return a certificate by id.
     */
    public Optional<Certificate> getCertificateById(Long id) {
        return certificateRepository.findById(id);
    }

    /**
     * Delete a certificate by id.
     */
    @Transactional
    public void deleteCertificate(Long id) {
        certificateRepository.deleteById(id);
    }

    /**
     * Set the trusted flag on a certificate.
     */
    @Transactional
    public Certificate setTrusted(Long id, boolean trusted) {
        Certificate cert = certificateRepository.findById(id)
                .orElseThrow(() -> new CertificateNotFoundException("Certificate not found: " + id));
        cert.setTrusted(trusted);
        return certificateRepository.save(cert);
    }

    // -------------------------------------------------------------------------
    // Signing
    // -------------------------------------------------------------------------

    /**
     * Sign a MimeMessage with our private key and return the signed message.
     */
    public MimeMessage signMessage(MimeMessage message) throws Exception {
        if (privateKey == null || ownCertificate == null) {
            throw new IllegalStateException("S/MIME signing key is not loaded");
        }

        // Build capabilities attribute
        SMIMECapabilityVector caps = new SMIMECapabilityVector();
        caps.addCapability(SMIMECapability.aES256_CBC);
        caps.addCapability(SMIMECapability.aES128_CBC);
        caps.addCapability(SMIMECapability.dES_EDE3_CBC);

        ASN1EncodableVector signedAttrs = new ASN1EncodableVector();
        signedAttrs.add(new SMIMECapabilitiesAttribute(caps));

        SMIMESignedGenerator signer = new SMIMESignedGenerator();
        signer.addSignerInfoGenerator(
                new JcaSimpleSignerInfoGeneratorBuilder()
                        .setProvider("BC")
                        .setSignedAttributeGenerator(
                                new org.bouncycastle.cms.SimpleAttributeTableGenerator(new AttributeTable(signedAttrs)))
                        .build(properties.getSmime().getSignatureAlgorithm(), privateKey, ownCertificate)
        );
        signer.addCertificates(new JcaCertStore(Collections.singletonList(ownCertificate)));

        message.saveChanges();
        MimeBodyPart bodyPart = messageToBodyPart(message);
        MimeMultipart signedMultipart = signer.generate(bodyPart);

        MimeMessage signed = new MimeMessage(getSession());
        copyHeaders(message, signed);
        signed.setContent(signedMultipart);
        signed.saveChanges();
        return signed;
    }

    // -------------------------------------------------------------------------
    // Encryption
    // -------------------------------------------------------------------------

    /**
     * Encrypt a MimeMessage for the given recipient email addresses.
     * Uses the first active certificate found for each recipient.
     */
    public MimeMessage encryptMessage(MimeMessage message, List<String> recipientEmails) throws Exception {
        SMIMEEnvelopedGenerator gen = new SMIMEEnvelopedGenerator();

        boolean hasRecipient = false;
        for (String email : recipientEmails) {
            Optional<X509Certificate> cert = findEncryptionCertificate(email);
            if (cert.isPresent()) {
                gen.addRecipientInfoGenerator(
                        new JceKeyTransRecipientInfoGenerator(cert.get()).setProvider("BC"));
                hasRecipient = true;
            } else {
                log.warn("No S/MIME certificate found for recipient: {}", email);
            }
        }

        // Optionally encrypt to self so we can read sent messages
        if (properties.getSmime().isAlwaysEncryptToSelf() && ownCertificate != null) {
            gen.addRecipientInfoGenerator(
                    new JceKeyTransRecipientInfoGenerator(ownCertificate).setProvider("BC"));
            hasRecipient = true;
        }

        if (!hasRecipient) {
            throw new CertificateNotFoundException(
                    "No S/MIME encryption certificates found for any recipient");
        }

        message.saveChanges();
        MimeBodyPart bodyPart = messageToBodyPart(message);
        MimeBodyPart encryptedBodyPart = gen.generate(
                bodyPart,
                new JceCMSContentEncryptorBuilder(resolveEncryptionAlgorithm()).setProvider("BC").build()
        );

        MimeMessage encrypted = new MimeMessage(getSession());
        copyHeaders(message, encrypted);
        encrypted.setContent(encryptedBodyPart.getContent(), encryptedBodyPart.getContentType());
        encrypted.saveChanges();
        return encrypted;
    }

    // -------------------------------------------------------------------------
    // Decryption
    // -------------------------------------------------------------------------

    /**
     * Decrypt an encrypted MimeMessage using our private key.
     * Returns the decrypted MimeMessage.
     */
    public MimeMessage decryptMessage(MimeMessage message) throws DecryptionException {
        if (privateKey == null) {
            throw new DecryptionException("S/MIME private key is not loaded – cannot decrypt");
        }
        try {
            SMIMEEnveloped enveloped = new SMIMEEnveloped(message);
            RecipientInformationStore recipients = enveloped.getRecipientInfos();

            // setProvider() returns the parent type JceKeyTransRecipient, so we split the call.
            JceKeyTransEnvelopedRecipient recipient = new JceKeyTransEnvelopedRecipient(privateKey);
            recipient.setProvider("BC");

            byte[] decryptedBytes = null;
            for (RecipientInformation info : recipients.getRecipients()) {
                try {
                    decryptedBytes = info.getContent(recipient);
                    break;
                } catch (Exception ignored) {
                    // not our recipient – try the next one
                }
            }

            if (decryptedBytes == null) {
                throw new DecryptionException("Could not decrypt: message not addressed to our key");
            }

            MimeBodyPart decryptedBodyPart = SMIMEUtil.toMimeBodyPart(decryptedBytes);

            MimeMessage decrypted = new MimeMessage(getSession());
            copyHeaders(message, decrypted);
            decrypted.setContent(decryptedBodyPart.getContent(), decryptedBodyPart.getContentType());
            decrypted.saveChanges();
            return decrypted;

        } catch (DecryptionException e) {
            throw e;
        } catch (Exception e) {
            throw new DecryptionException("Failed to decrypt S/MIME message: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Signature verification
    // -------------------------------------------------------------------------

    /**
     * Verify the S/MIME signature on a MimeMessage.
     */
    @SuppressWarnings("unchecked") // BC Store.getMatches() uses raw generic type
    public VerificationResult verifySignature(MimeMessage message) {
        VerificationResult result = new VerificationResult();
        try {
            SMIMESigned smimeSigned = buildSMIMESigned(message);
            if (smimeSigned == null) {
                result.setValid(false);
                result.setError("Message does not contain a recognisable S/MIME signature");
                return result;
            }

            Store<X509CertificateHolder> certs = smimeSigned.getCertificates();
            SignerInformationStore signerStore = smimeSigned.getSignerInfos();
            boolean allValid = true;

            for (SignerInformation signer : signerStore.getSigners()) {
                Collection<X509CertificateHolder> matches = certs.getMatches(signer.getSID());
                if (matches.isEmpty()) {
                    result.setValid(false);
                    result.setError("No certificate found for signer in message");
                    return result;
                }

                X509CertificateHolder holder = matches.iterator().next();
                X509Certificate x509 = new JcaX509CertificateConverter()
                        .setProvider("BC").getCertificate(holder);

                boolean sigOk = signer.verify(
                        new JcaSimpleSignerInfoVerifierBuilder().setProvider("BC").build(x509));
                if (!sigOk) {
                    allValid = false;
                }

                result.setSignerEmail(extractEmailFromCertificate(x509));
                result.setSignerCertificate(x509);
                result.setCertificateThumbprint(computeThumbprint(x509));

                try {
                    x509.checkValidity();
                } catch (CertificateExpiredException ex) {
                    result.setCertificateExpired(true);
                    allValid = false;
                } catch (CertificateNotYetValidException ex) {
                    result.setCertificateNotYetValid(true);
                    allValid = false;
                }

                // Check our trust store
                if (result.getSignerEmail() != null) {
                    Optional<Certificate> stored = certificateRepository
                            .findByThumbprint(result.getCertificateThumbprint());
                    result.setTrusted(stored.isPresent() && stored.get().isTrusted());
                }
            }

            result.setValid(allValid);
        } catch (Exception e) {
            result.setValid(false);
            result.setError("Signature verification error: " + e.getMessage());
            log.error("Error verifying S/MIME signature", e);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // High-level pipeline methods used by EmailSenderService / EmailReceiverService
    // -------------------------------------------------------------------------

    /**
     * Process an incoming MimeMessage: decrypt if needed, then verify signature.
     * Returns a {@link IncomingSmimeResult} containing the (possibly decrypted) message
     * and the S/MIME metadata to apply to the EmailMessage entity.
     */
    public IncomingSmimeResult processIncoming(MimeMessage message) {
        IncomingSmimeResult r = new IncomingSmimeResult(message);

        if (!properties.getSmime().isEnabled()) {
            r.setStatus(SmimeStatus.NONE);
            return r;
        }

        MimeMessage current = message;
        boolean wasEncrypted = false;

        // --- Step 1: decrypt ---
        if (isEncrypted(current)) {
            wasEncrypted = true;
            try {
                current = decryptMessage(current);
                r.setEncrypted(true);
                r.setProcessedMessage(current);
            } catch (DecryptionException e) {
                log.error("Failed to decrypt S/MIME message: {}", e.getMessage());
                r.setEncrypted(true);
                r.setStatus(SmimeStatus.DECRYPTION_FAILED);
                r.setError(e.getMessage());
                return r;
            }
        }

        // --- Step 2: verify signature ---
        if (isSigned(current)) {
            r.setSigned(true);
            VerificationResult vr = verifySignature(current);
            r.setSignatureValid(vr.isValid());
            r.setSignerEmail(vr.getSignerEmail());
            r.setCertificateThumbprint(vr.getCertificateThumbprint());

            // Persist the signer certificate for future encryption
            if (properties.getSmime().isAutoDownloadCertificates()) {
                discoverCertificateFromEmail(current);
            }

            if (!vr.isValid()) {
                if (vr.isCertificateExpired()) {
                    r.setStatus(SmimeStatus.CERTIFICATE_EXPIRED);
                } else if (!vr.isTrusted()) {
                    r.setStatus(SmimeStatus.CERTIFICATE_UNTRUSTED);
                } else {
                    r.setStatus(SmimeStatus.SIGNATURE_INVALID);
                }
                r.setError(vr.getError());
            } else {
                r.setStatus(wasEncrypted ? SmimeStatus.SIGNED_AND_ENCRYPTED : SmimeStatus.SIGNED);
            }
        } else if (wasEncrypted) {
            r.setStatus(SmimeStatus.ENCRYPTED);
        } else {
            r.setStatus(SmimeStatus.NONE);
        }

        return r;
    }

    /**
     * Prepare an outgoing MimeMessage: sign and/or encrypt as configured.
     * When S/MIME is disabled, the original message is returned unchanged.
     */
    public MimeMessage prepareOutgoing(MimeMessage message, List<String> recipientEmails) throws Exception {
        if (!properties.getSmime().isEnabled()) {
            return message;
        }

        MimeMessage result = message;

        // Sign first, then encrypt (standard S/MIME order)
        if (privateKey != null && ownCertificate != null) {
            result = signMessage(result);
        }

        if (recipientEmails != null && !recipientEmails.isEmpty()) {
            try {
                result = encryptMessage(result, recipientEmails);
            } catch (CertificateNotFoundException e) {
                log.warn("Could not encrypt for some/all recipients: {}", e.getMessage());
                if (properties.getSmime().isRequireEncryption()) {
                    throw e;
                }
                // Send signed-only if encryption certificates are missing
            }
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Certificate lookup helpers
    // -------------------------------------------------------------------------

    /**
     * Find the best available encryption certificate for a given email address.
     */
    public Optional<X509Certificate> findEncryptionCertificate(String emailAddress) {
        // Prefer DUAL_USE, then ENCRYPTION, then SIGNING
        for (CertificateType type : new CertificateType[]{
                CertificateType.DUAL_USE, CertificateType.ENCRYPTION, CertificateType.SIGNING}) {
            Optional<Certificate> stored = certificateRepository
                    .findByEmailAddressAndType(emailAddress, type);
            if (stored.isPresent()) {
                try {
                    return Optional.of(parseCertificate(stored.get().getCertificateData()));
                } catch (Exception e) {
                    log.warn("Could not parse stored certificate for {}: {}", emailAddress, e.getMessage());
                }
            }
        }
        return Optional.empty();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private boolean isSigned(MimeMessage message) {
        try {
            String ct = message.getContentType();
            if (ct == null) return false;
            String ctLower = ct.toLowerCase();
            return ctLower.contains("multipart/signed")
                    || (ctLower.contains("application/pkcs7-mime") && ctLower.contains("signed-data"))
                    || (ctLower.contains("application/x-pkcs7-mime") && ctLower.contains("signed-data"));
        } catch (MessagingException e) {
            return false;
        }
    }

    private boolean isEncrypted(MimeMessage message) {
        try {
            String ct = message.getContentType();
            if (ct == null) return false;
            String ctLower = ct.toLowerCase();
            return (ctLower.contains("application/pkcs7-mime") && ctLower.contains("enveloped-data"))
                    || (ctLower.contains("application/x-pkcs7-mime") && ctLower.contains("enveloped-data"));
        } catch (MessagingException e) {
            return false;
        }
    }

    private SMIMESigned buildSMIMESigned(MimeMessage message) {
        try {
            String ct = message.getContentType().toLowerCase();
            if (ct.contains("multipart/signed")) {
                return new SMIMESigned((MimeMultipart) message.getContent());
            } else if (ct.contains("signed-data")) {
                return new SMIMESigned(message);
            }
        } catch (Exception e) {
            log.debug("Could not build SMIMESigned: {}", e.getMessage());
        }
        return null;
    }

    private MimeBodyPart messageToBodyPart(MimeMessage message) throws Exception {
        // Copy the message content into a MimeBodyPart for use with BC S/MIME generators.
        // MimeBodyPart has no saveChanges(); headers are set via setDataHandler.
        MimeBodyPart bodyPart = new MimeBodyPart();
        bodyPart.setDataHandler(message.getDataHandler());
        bodyPart.setHeader("Content-Type", message.getContentType());
        return bodyPart;
    }

    private Session getSession() {
        return Session.getInstance(System.getProperties());
    }

    private void copyHeaders(MimeMessage source, MimeMessage target) throws MessagingException {
        Enumeration<?> headers = source.getAllHeaders();
        while (headers.hasMoreElements()) {
            Header h = (Header) headers.nextElement();
            String name = h.getName();
            // Skip content headers – they will be replaced by the S/MIME wrapper
            if (!name.equalsIgnoreCase("Content-Type")
                    && !name.equalsIgnoreCase("Content-Transfer-Encoding")
                    && !name.equalsIgnoreCase("MIME-Version")) {
                target.setHeader(name, h.getValue());
            }
        }
    }

    private Certificate persistCertificate(X509Certificate cert, String emailAddress,
                                            CertificateType type, boolean trusted) throws Exception {
        String thumbprint = computeThumbprint(cert);

        // 1. Exact match by thumbprint → no-op
        Optional<Certificate> byThumbprint = certificateRepository.findByThumbprint(thumbprint);
        if (byThumbprint.isPresent()) {
            log.debug("Certificate already persisted (thumbprint={})", thumbprint);
            return byThumbprint.get();
        }

        // Resolve the email address to store
        String resolvedEmail = (emailAddress != null && !emailAddress.isEmpty())
                ? emailAddress
                : extractEmailFromCertificate(cert);
        if (resolvedEmail == null) {
            resolvedEmail = "cert-" + cert.getSerialNumber().toString(16) + "@smime.local";
        }

        // 2. Existing record for same email address → update in place (unique constraint)
        List<Certificate> byEmail = certificateRepository.findByEmailAddress(resolvedEmail);
        if (!byEmail.isEmpty()) {
            Certificate existing = byEmail.get(0);
            existing.setSerialNumber(cert.getSerialNumber().toString(16));
            existing.setCertificateData(cert.getEncoded());
            existing.setSubjectDN(cert.getSubjectX500Principal().getName());
            existing.setIssuerDN(cert.getIssuerX500Principal().getName());
            existing.setNotBefore(LocalDateTime.ofInstant(cert.getNotBefore().toInstant(), ZoneId.systemDefault()));
            existing.setNotAfter(LocalDateTime.ofInstant(cert.getNotAfter().toInstant(), ZoneId.systemDefault()));
            existing.setThumbprint(thumbprint);
            existing.setType(type);
            existing.setImportedAt(LocalDateTime.now());
            log.info("Updated existing certificate for: {}", resolvedEmail);
            return certificateRepository.save(existing);
        }

        // 3. Brand-new entry
        Certificate entity = buildCertificateEntity(cert, type, trusted);
        entity.setEmailAddress(resolvedEmail);
        return certificateRepository.save(entity);
    }

    private Certificate buildCertificateEntity(X509Certificate cert,
                                                CertificateType type, boolean trusted) throws Exception {
        String thumbprint = computeThumbprint(cert);
        String emailAddress = extractEmailFromCertificate(cert);

        // Key usages
        Set<String> keyUsages = new HashSet<>();
        boolean[] usageBits = cert.getKeyUsage();
        if (usageBits != null) {
            String[] names = {
                    "digitalSignature", "nonRepudiation", "keyEncipherment", "dataEncipherment",
                    "keyAgreement", "keyCertSign", "cRLSign", "encipherOnly", "decipherOnly"
            };
            for (int i = 0; i < usageBits.length && i < names.length; i++) {
                if (usageBits[i]) keyUsages.add(names[i]);
            }
        }

        // Extended key usages
        Set<String> extKeyUsages = new HashSet<>();
        List<String> ekus = cert.getExtendedKeyUsage();
        if (ekus != null) {
            extKeyUsages.addAll(ekus);
        }

        // Status
        CertificateStatus status;
        try {
            cert.checkValidity();
            status = CertificateStatus.ACTIVE;
        } catch (CertificateExpiredException e) {
            status = CertificateStatus.EXPIRED;
        } catch (CertificateNotYetValidException e) {
            status = CertificateStatus.PENDING_VALIDATION;
        }

        return Certificate.builder()
                .emailAddress(emailAddress)
                .serialNumber(cert.getSerialNumber().toString(16))
                .certificateData(cert.getEncoded())
                .subjectDN(cert.getSubjectX500Principal().getName())
                .issuerDN(cert.getIssuerX500Principal().getName())
                .notBefore(LocalDateTime.ofInstant(cert.getNotBefore().toInstant(), ZoneId.systemDefault()))
                .notAfter(LocalDateTime.ofInstant(cert.getNotAfter().toInstant(), ZoneId.systemDefault()))
                .thumbprint(thumbprint)
                .type(type)
                .status(status)
                .trusted(trusted)
                .importedAt(LocalDateTime.now())
                .keyUsages(keyUsages)
                .extendedKeyUsages(extKeyUsages)
                .build();
    }

    private X509Certificate parseCertificate(byte[] der) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509", "BC");
        return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(der));
    }

    private byte[] decodePem(String pem) {
        String base64 = pem
                .replaceAll("-----BEGIN CERTIFICATE-----", "")
                .replaceAll("-----END CERTIFICATE-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }

    private String computeThumbprint(X509Certificate cert) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] hash = md.digest(cert.getEncoded());
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String extractEmailFromCertificate(X509Certificate cert) {
        try {
            // Prefer Subject Alternative Name (rfc822Name, type 1)
            Collection<List<?>> sans = cert.getSubjectAlternativeNames();
            if (sans != null) {
                for (List<?> san : sans) {
                    if (san.size() >= 2 && Integer.valueOf(1).equals(san.get(0))) {
                        Object value = san.get(1);
                        if (value instanceof String && ((String) value).contains("@")) {
                            return (String) value;
                        }
                    }
                }
            }
            // Fall back to Subject DN fields
            String dn = cert.getSubjectX500Principal().getName();
            for (String part : dn.split(",")) {
                part = part.trim();
                String upper = part.toUpperCase();
                if (upper.startsWith("E=") || upper.startsWith("EMAILADDRESS=") || upper.startsWith("CN=")) {
                    String value = part.substring(part.indexOf('=') + 1);
                    if (value.contains("@")) {
                        return value;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract email from certificate: {}", e.getMessage());
        }
        return null;
    }

    private org.bouncycastle.asn1.ASN1ObjectIdentifier resolveEncryptionAlgorithm() {
        String alg = properties.getSmime().getEncryptionAlgorithm();
        if ("AES128_CBC".equalsIgnoreCase(alg)) {
            return CMSAlgorithm.AES128_CBC;
        }
        if ("DES_EDE3_CBC".equalsIgnoreCase(alg)) {
            return CMSAlgorithm.DES_EDE3_CBC;
        }
        return CMSAlgorithm.AES256_CBC; // Default
    }

    // -------------------------------------------------------------------------
    // Result types
    // -------------------------------------------------------------------------

    /**
     * Result of processing an incoming S/MIME message.
     */
    @Getter
    public static class IncomingSmimeResult {
        private MimeMessage processedMessage;
        private SmimeStatus status = SmimeStatus.NONE;
        private boolean signed;
        private boolean encrypted;
        private boolean signatureValid;
        private String signerEmail;
        private String certificateThumbprint;
        private String error;

        public IncomingSmimeResult(MimeMessage original) {
            this.processedMessage = original;
        }

        public void setProcessedMessage(MimeMessage m) { this.processedMessage = m; }
        public void setStatus(SmimeStatus s) { this.status = s; }
        public void setSigned(boolean b) { this.signed = b; }
        public void setEncrypted(boolean b) { this.encrypted = b; }
        public void setSignatureValid(boolean b) { this.signatureValid = b; }
        public void setSignerEmail(String e) { this.signerEmail = e; }
        public void setCertificateThumbprint(String t) { this.certificateThumbprint = t; }
        public void setError(String e) { this.error = e; }
    }

    /**
     * Result of verifying an S/MIME signature.
     */
    @Getter
    public static class VerificationResult {
        private boolean valid;
        private String signerEmail;
        private X509Certificate signerCertificate;
        private String certificateThumbprint;
        private boolean certificateExpired;
        private boolean certificateNotYetValid;
        private boolean trusted;
        private String error;

        public void setValid(boolean v) { this.valid = v; }
        public void setSignerEmail(String e) { this.signerEmail = e; }
        public void setSignerCertificate(X509Certificate c) { this.signerCertificate = c; }
        public void setCertificateThumbprint(String t) { this.certificateThumbprint = t; }
        public void setCertificateExpired(boolean b) { this.certificateExpired = b; }
        public void setCertificateNotYetValid(boolean b) { this.certificateNotYetValid = b; }
        public void setTrusted(boolean b) { this.trusted = b; }
        public void setError(String e) { this.error = e; }
    }
}
