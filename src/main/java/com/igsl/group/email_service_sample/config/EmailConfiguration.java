package com.igsl.group.email_service_sample.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Store;
import java.util.Properties;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@RequiredArgsConstructor
public class EmailConfiguration {
    
    private final EmailProperties emailProperties;
    
    @Bean
    public JavaMailSender javaMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(emailProperties.getSmtp().getHost());
        mailSender.setPort(emailProperties.getSmtp().getPort());
        mailSender.setUsername(emailProperties.getSmtp().getUsername());
        mailSender.setPassword(emailProperties.getSmtp().getPassword());
        
        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", emailProperties.getSmtp().isAuth());
        props.put("mail.smtp.starttls.enable", emailProperties.getSmtp().isStarttls());
        props.put("mail.smtp.starttls.required", emailProperties.getSmtp().isStarttls());
        props.put("mail.smtp.connectiontimeout", emailProperties.getSmtp().getConnectionTimeout());
        props.put("mail.smtp.timeout", emailProperties.getSmtp().getTimeout());
        props.put("mail.debug", emailProperties.getGeneral().isDebugEnabled());
        
        // SSL/TLS Configuration
        if (emailProperties.getSmtp().isSsl()) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.port", emailProperties.getSmtp().getPort());
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.socketFactory.fallback", "false");
        }
        
        // Trust all certificates (for testing only - remove in production)
        if (emailProperties.getSmtp().isTrustAllCerts()) {
            props.put("mail.smtp.ssl.trust", "*");
        }
        
        return mailSender;
    }
    
    @Bean
    public Store imapStore() throws Exception {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", emailProperties.getImap().getHost());
        props.put("mail.imaps.port", emailProperties.getImap().getPort());
        props.put("mail.imaps.ssl.enable", emailProperties.getImap().isSsl());
        props.put("mail.imaps.ssl.trust", "*");
        props.put("mail.debug", emailProperties.getGeneral().isDebugEnabled());
        
        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(
                    emailProperties.getImap().getUsername(),
                    emailProperties.getImap().getPassword()
                );
            }
        });
        
        Store store = session.getStore("imaps");
        store.connect(
            emailProperties.getImap().getHost(),
            emailProperties.getImap().getUsername(),
            emailProperties.getImap().getPassword()
        );
        
        return store;
    }
    
    @Bean(name = "emailTaskExecutor")
    public Executor emailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("email-async-");
        executor.initialize();
        return executor;
    }
}