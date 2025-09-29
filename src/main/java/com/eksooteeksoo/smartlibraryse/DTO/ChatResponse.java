package com.eksooteeksoo.smartlibraryse.DTO;

import com.eksooteeksoo.smartlibraryse.Model.Book;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ChatResponse {
    private String response;
    private boolean success;
    private String error;
    private List<Book> books; // Added for book search results
    private String responseType; // "text" or "books"

    public ChatResponse(String response) {
        this.response = response;
        this.success = true;
        this.error = null;
        this.books = null;
        this.responseType = "text";
    }
    
    public ChatResponse(List<Book> books, String responseMessage) {
        this.response = responseMessage;
        this.success = true;
        this.error = null;
        this.books = books;
        this.responseType = "books";
    }

    public static ChatResponse error(String error) {
        return new ChatResponse(null, false, error, null, "error");
    }
}
