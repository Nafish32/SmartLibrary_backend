package com.eksooteeksoo.smartlibraryse.DTO;

import com.eksooteeksoo.smartlibraryse.Model.BookBooking;
import com.eksooteeksoo.smartlibraryse.Model.BookingStatus;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class BookBookingResponseDTO {
    private Long id;
    private UserResponseDTO user;
    private BookResponseDTO book;
    private LocalDateTime bookingDate;
    private LocalDateTime returnDate;
    private BookingStatus status;
    private String notes;
    private LocalDateTime dueDate; // Calculate due date (e.g., 14 days from booking)

    public BookBookingResponseDTO(BookBooking booking) {
        this.id = booking.getId();
        this.user = new UserResponseDTO(booking.getUser());
        this.book = new BookResponseDTO(booking.getBook());
        this.bookingDate = booking.getBookingDate();
        this.returnDate = booking.getReturnDate();
        this.status = booking.getStatus();
        this.notes = booking.getNotes();
        // Calculate due date (14 days from booking date)
        this.dueDate = booking.getBookingDate() != null ? 
            booking.getBookingDate().plusDays(14) : null;
    }

    @Data
    public static class UserResponseDTO {
        private Long id;
        private String userName;
        private String fullName;
        private String email;

        public UserResponseDTO(com.eksooteeksoo.smartlibraryse.Model.Usr user) {
            this.id = user.getId();
            this.userName = user.getUserName();
            this.fullName = user.getFullName();
            this.email = user.getEmail();
        }
    }

    @Data
    public static class BookResponseDTO {
        private Long id;
        private String title;
        private String author;
        private Integer publishedYear;
        private String isbn;
        private String genre;

        public BookResponseDTO(com.eksooteeksoo.smartlibraryse.Model.Book book) {
            this.id = book.getId();
            this.title = book.getTitle();
            this.author = book.getAuthor();
            this.publishedYear = book.getPublishedYear();
            this.isbn = book.getIsbn();
            this.genre = book.getGenre();
        }
    }
}
