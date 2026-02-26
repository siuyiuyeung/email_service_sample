package com.igsl.group.email_service_sample.controller;

import com.igsl.group.email_service_sample.dto.CertificateDTO;
import com.igsl.group.email_service_sample.dto.ImportCertificateRequest;
import com.igsl.group.email_service_sample.model.Certificate;
import com.igsl.group.email_service_sample.service.SMimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST endpoints for managing S/MIME certificates.
 *
 * Base path: /api/v1/smime/certificates
 */
@RestController
@RequestMapping("/api/v1/smime/certificates")
@RequiredArgsConstructor
@Slf4j
public class SmimeCertificateController {

    private final SMimeService smimeService;

    /**
     * List all stored certificates.
     */
    @GetMapping
    public ResponseEntity<List<CertificateDTO>> listCertificates() {
        List<CertificateDTO> dtos = smimeService.getAllCertificates().stream()
                .map(CertificateDTO::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Retrieve a single certificate by id.
     */
    @GetMapping("/{id}")
    public ResponseEntity<CertificateDTO> getCertificate(@PathVariable Long id) {
        return smimeService.getCertificateById(id)
                .map(c -> ResponseEntity.ok(CertificateDTO.from(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Import a PEM-encoded certificate.
     * The email address may be provided in the request body or will be extracted
     * from the certificate's Subject/SAN automatically.
     */
    @PostMapping
    public ResponseEntity<CertificateDTO> importCertificate(
            @Valid @RequestBody ImportCertificateRequest request) {
        try {
            Certificate cert = smimeService.importCertificateFromPem(
                    request.getPemCertificate(), request.getEmailAddress());
            return ResponseEntity.ok(CertificateDTO.from(cert));
        } catch (Exception e) {
            log.error("Failed to import certificate", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Mark a certificate as trusted (or untrusted).
     */
    @PutMapping("/{id}/trust")
    public ResponseEntity<CertificateDTO> setTrusted(
            @PathVariable Long id,
            @RequestParam boolean trusted) {
        Certificate cert = smimeService.setTrusted(id, trusted);
        return ResponseEntity.ok(CertificateDTO.from(cert));
    }

    /**
     * Delete a certificate by id.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCertificate(@PathVariable Long id) {
        smimeService.deleteCertificate(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Manually trigger expiry validation across all stored certificates.
     */
    @PostMapping("/validate")
    public ResponseEntity<Void> validateCertificates() {
        smimeService.validateCertificateStatuses();
        return ResponseEntity.ok().build();
    }
}
