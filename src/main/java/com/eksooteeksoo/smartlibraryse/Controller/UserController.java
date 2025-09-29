package com.eksooteeksoo.smartlibraryse.Controller;

import com.eksooteeksoo.smartlibraryse.DTO.BookBookingDTO;
import com.eksooteeksoo.smartlibraryse.DTO.BookBookingResponseDTO;
import com.eksooteeksoo.smartlibraryse.DTO.ChatRequest;
import com.eksooteeksoo.smartlibraryse.DTO.ChatResponse;
import com.eksooteeksoo.smartlibraryse.Model.Book;
import com.eksooteeksoo.smartlibraryse.Model.BookBooking;
import com.eksooteeksoo.smartlibraryse.Service.AIService;
import com.eksooteeksoo.smartlibraryse.Service.BookService;
import com.eksooteeksoo.smartlibraryse.Service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user")
@CrossOrigin(origins = "*", maxAge = 3600)
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final BookService bookService;
    private final UserService userService;
    private final AIService aiService;

    public UserController(BookService bookService,
                         UserService userService,
                         AIService aiService) {
        this.bookService = bookService;
        this.userService = userService;
        this.aiService = aiService;
    }

    // Public endpoints for book browsing (accessible to guests)
    @GetMapping("/books/available")
    public ResponseEntity<List<Book>> getAvailableBooks() {
        logger.debug("Fetching available books for guest/user");
        return ResponseEntity.ok(bookService.getAvailableBooks());
    }

    @GetMapping("/books/search")
    public ResponseEntity<List<Book>> searchBooks(@RequestParam String query) {
        logger.debug("Searching books with query: {}", query);
        return ResponseEntity.ok(bookService.searchAvailableBooks(query));
    }

    @GetMapping("/books/{id}")
    public ResponseEntity<Book> getBookById(@PathVariable Long id) {
        logger.debug("Fetching book with id: {}", id);
        Book book = bookService.getBookById(id);
        if (book.getQuantity() > 0) {
            return ResponseEntity.ok(book);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // AI Chat endpoint (accessible to all users including guests)
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest chatRequest) {
        logger.debug("Processing AI chat request in language: {}", chatRequest.getLanguage());
        ChatResponse response = aiService.processChat(chatRequest);
        return ResponseEntity.ok(response);
    }

    // Authenticated user endpoints
    @PostMapping("/books/book")
    public ResponseEntity<BookBookingResponseDTO> bookBook(@Valid @RequestBody BookBookingDTO bookingDTO,
                                               Authentication authentication) {
        String username = authentication.getName();
        logger.info("User {} booking book with id: {}", username, bookingDTO.getBookId());
        BookBooking booking = userService.bookBook(username, bookingDTO);
        return ResponseEntity.ok(new BookBookingResponseDTO(booking));
    }

    @GetMapping("/bookings")
    public ResponseEntity<List<BookBookingResponseDTO>> getUserBookings(Authentication authentication) {
        String username = authentication.getName();
        logger.debug("Fetching bookings for user: {}", username);
        List<BookBooking> bookings = userService.getUserBookings(username);
        List<BookBookingResponseDTO> bookingDTOs = bookings.stream()
                .map(BookBookingResponseDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(bookingDTOs);
    }

    @GetMapping("/bookings/active")
    public ResponseEntity<List<BookBookingResponseDTO>> getActiveBookings(Authentication authentication) {
        String username = authentication.getName();
        logger.debug("Fetching active bookings for user: {}", username);
        List<BookBooking> bookings = userService.getActiveBookings(username);
        List<BookBookingResponseDTO> bookingDTOs = bookings.stream()
                .map(BookBookingResponseDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(bookingDTOs);
    }

    @PutMapping("/bookings/{id}/return")
    public ResponseEntity<BookBookingResponseDTO> returnBook(@PathVariable Long id, Authentication authentication) {
        String username = authentication.getName();
        logger.info("User {} returning book for booking id: {}", username, id);
        BookBooking booking = userService.returnBook(id);
        return ResponseEntity.ok(new BookBookingResponseDTO(booking));
    }
}
