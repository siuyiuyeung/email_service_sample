package com.igsl.group.email_service_sample.event;

import com.igsl.group.email_service_sample.model.EmailMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class EmailEventPublisher {
    private final ApplicationEventPublisher eventPublisher;
    
    public void publishNewEmailEvent(EmailMessage email) {
        eventPublisher.publishEvent(new NewEmailEvent(email, LocalDateTime.now()));
    }
}