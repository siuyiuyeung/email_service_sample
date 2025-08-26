package com.igsl.group.email_service_sample.config;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.security.Security;

@Configuration
public class SecurityConfiguration {
    
    @PostConstruct
    public void init() {
        // Register Bouncy Castle provider for S/MIME support
        Security.addProvider(new BouncyCastleProvider());
    }
}