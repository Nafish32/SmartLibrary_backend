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
    private boolean allowBooking; // New field to indicate if booking is available
    private String bookingMessage; // Message about booking availability

    public ChatResponse(String response) {
        this.response = response;
        this.success = true;
        this.error = null;
        this.books = null;
        this.responseType = "text";
        this.allowBooking = false;
        this.bookingMessage = null;
    }
    
    public ChatResponse(List<Book> books, String responseMessage) {
        this.response = responseMessage;
        this.success = true;
        this.error = null;
        this.books = books;
        this.responseType = "books";
        this.allowBooking = true;
        this.bookingMessage = "You can book any of these books by clicking the 'Book Now' button.";
    }

    public static ChatResponse error(String error) {
        return new ChatResponse(null, false, error, null, "error", false, null);
    }
}
