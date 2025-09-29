package com.eksooteeksoo.smartlibraryse.Service;
import com.eksooteeksoo.smartlibraryse.DTO.ChatRequest;
import com.eksooteeksoo.smartlibraryse.DTO.ChatResponse;
public interface AIService {
    ChatResponse processChat(ChatRequest chatRequest);
}

