package com.igsl.group.email_service_sample.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class ImportCertificateRequest {

    /**
     * PEM-encoded X.509 certificate (BEGIN CERTIFICATE … END CERTIFICATE).
     */
    @NotBlank(message = "Certificate PEM data is required")
    private String pemCertificate;

    /**
     * Email address to associate with this certificate.
     * If omitted, the address is extracted from the certificate's Subject/SAN.
     */
    private String emailAddress;
}
