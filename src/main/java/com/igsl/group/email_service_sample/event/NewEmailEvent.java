package com.igsl.group.email_service_sample.event;

import com.igsl.group.email_service_sample.model.EmailMessage;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class NewEmailEvent {
    private final EmailMessage emailMessage;
    private final LocalDateTime timestamp;
}