package com.eksooteeksoo.smartlibraryse.Controller;

import com.eksooteeksoo.smartlibraryse.DTO.BookBookingResponseDTO;
import com.eksooteeksoo.smartlibraryse.DTO.BookDTO;
import com.eksooteeksoo.smartlibraryse.DTO.UserRegistrationDTO;
import com.eksooteeksoo.smartlibraryse.Model.Book;
import com.eksooteeksoo.smartlibraryse.Model.BookBooking;
import com.eksooteeksoo.smartlibraryse.Model.Usr;
import com.eksooteeksoo.smartlibraryse.Repository.BookBookingRepository;
import com.eksooteeksoo.smartlibraryse.Service.BookService;
import com.eksooteeksoo.smartlibraryse.Service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    private final BookService bookService;
    private final UserService userService;
    private final BookBookingRepository bookBookingRepository;

    public AdminController(BookService bookService,
                          UserService userService,
                          BookBookingRepository bookBookingRepository) {
        this.bookService = bookService;
        this.userService = userService;
        this.bookBookingRepository = bookBookingRepository;
    }

    // Book Management ok
    @GetMapping("/books")
    public ResponseEntity<List<Book>> getAllBooks() {
        logger.debug("Admin fetching all books");
        return ResponseEntity.ok(bookService.getAllBooks());
    }
    //ok
    @PostMapping("/books")
    public ResponseEntity<Book> createBook(@Valid @RequestBody BookDTO bookDTO) {
        logger.info("Admin creating new book: {}", bookDTO.getTitle());
        Book book = bookService.createBook(bookDTO);
        return ResponseEntity.ok(book);
    }
    //ok
    @PutMapping("/books/{id}")
    public ResponseEntity<Book> updateBook(@PathVariable Long id, @Valid @RequestBody BookDTO bookDTO) {
        logger.info("Admin updating book with id: {}", id);
        Book book = bookService.updateBook(id, bookDTO);
        return ResponseEntity.ok(book);
    }

    @DeleteMapping("/books/{id}")
    public ResponseEntity<String> deleteBook(@PathVariable Long id) {
        logger.info("Admin deleting book with id: {}", id);
        bookService.deleteBook(id);
        return ResponseEntity.ok("Book deleted successfully!");
    }

    @PutMapping("/books/{id}/quantity")
    public ResponseEntity<Book> updateBookQuantity(@PathVariable Long id, @RequestParam int quantity) {
        logger.info("Admin updating quantity for book id: {} to {}", id, quantity);
        Book book = bookService.getBookById(id);
        BookDTO bookDTO = new BookDTO();
        bookDTO.setTitle(book.getTitle());
        bookDTO.setAuthor(book.getAuthor());
        bookDTO.setPublishedYear(book.getPublishedYear());
        bookDTO.setQuantity(quantity);
        bookDTO.setIsbn(book.getIsbn());
        bookDTO.setGenre(book.getGenre());
        bookDTO.setDescription(book.getDescription());

        Book updatedBook = bookService.updateBook(id, bookDTO);
        return ResponseEntity.ok(updatedBook);
    }

    // User Management
    @GetMapping("/users")
    public ResponseEntity<List<Usr>> getAllUsers() {
        logger.debug("Admin fetching all users");
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<Usr> getUserById(@PathVariable Long id) {
        logger.debug("Admin fetching user with id: {}", id);
        Usr user = userService.getUserById(id);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<Usr> updateUser(@PathVariable Long id, @Valid @RequestBody UserRegistrationDTO userDTO) {
        logger.info("Admin updating user with id: {}", id);
        Usr user = userService.updateUser(id, userDTO);
        return ResponseEntity.ok(user);
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<String> deleteUser(@PathVariable Long id) {
        logger.info("Admin deleting user with id: {}", id);
        userService.deleteUser(id);
        return ResponseEntity.ok("User deleted successfully!");
    }

    // Booking Management
    @GetMapping("/bookings")
    public ResponseEntity<List<BookBookingResponseDTO>> getAllBookings() {
        logger.debug("Admin fetching all bookings");
        List<BookBooking> bookings = bookBookingRepository.findAllWithUserAndBook();
        List<BookBookingResponseDTO> bookingDTOs = bookings.stream()
                .map(BookBookingResponseDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(bookingDTOs);
    }

    @PutMapping("/bookings/{id}/return")
    public ResponseEntity<BookBookingResponseDTO> returnBook(@PathVariable Long id) {
        logger.info("Admin processing book return for booking id: {}", id);
        BookBooking booking = userService.returnBook(id);
        return ResponseEntity.ok(new BookBookingResponseDTO(booking));
    }
}
