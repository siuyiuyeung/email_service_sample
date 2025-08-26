package com.igsl.group.email_service_sample.model;

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