package com.eksooteeksoo.smartlibraryse.DTO;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequest {
    @NotBlank(message = "Message is required")
    private String message;

    private String language; // "en" or "bn" for English or Bangla
}
