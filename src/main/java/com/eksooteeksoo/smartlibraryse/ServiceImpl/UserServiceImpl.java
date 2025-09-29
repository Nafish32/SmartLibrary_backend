package com.eksooteeksoo.smartlibraryse.ServiceImpl;

import com.eksooteeksoo.smartlibraryse.DTO.BookBookingDTO;
import com.eksooteeksoo.smartlibraryse.DTO.UserRegistrationDTO;
import com.eksooteeksoo.smartlibraryse.Model.*;
import com.eksooteeksoo.smartlibraryse.Repository.BookBookingRepository;
import com.eksooteeksoo.smartlibraryse.Repository.BookRepository;
import com.eksooteeksoo.smartlibraryse.Repository.UserRepository;
import com.eksooteeksoo.smartlibraryse.Service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@Transactional
public class UserServiceImpl implements UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final BookBookingRepository bookBookingRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.registration.key}")
    private String adminRegistrationKey;

    public UserServiceImpl(UserRepository userRepository,
                          BookRepository bookRepository,
                          BookBookingRepository bookBookingRepository,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.bookRepository = bookRepository;
        this.bookBookingRepository = bookBookingRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Usr> getAllUsers() {
        logger.debug("Fetching all users");
        return userRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Usr getUserById(Long id) {
        logger.debug("Fetching user with id: {}", id);
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));
    }

    @Override
    public Usr createUser(UserRegistrationDTO userDTO) {
        logger.info("Creating new user: {} with role: {}", userDTO.getUsername(), userDTO.getRole());

        if (userRepository.existsByUserName(userDTO.getUsername())) {
            throw new IllegalArgumentException("Username already exists: " + userDTO.getUsername());
        }

        if (userDTO.getEmail() != null && userRepository.existsByEmail(userDTO.getEmail())) {
            throw new IllegalArgumentException("Email already exists: " + userDTO.getEmail());
        }

        // Validate role and admin key
        Set<Role> userRoles;
        if ("ADMIN".equalsIgnoreCase(userDTO.getRole())) {
            if (userDTO.getAdminKey() == null || userDTO.getAdminKey().trim().isEmpty()) {
                throw new IllegalArgumentException("Admin key is required for admin registration");
            }
            if (!adminRegistrationKey.equals(userDTO.getAdminKey().trim())) {
                throw new IllegalArgumentException("Invalid admin key");
            }
            userRoles = Set.of(Role.ROLE_ADMIN, Role.ROLE_USER);
            logger.info("Admin user registration approved for: {}", userDTO.getUsername());
        } else if ("USER".equalsIgnoreCase(userDTO.getRole())) {
            userRoles = Set.of(Role.ROLE_USER);
        } else {
            throw new IllegalArgumentException("Invalid role. Must be either USER or ADMIN");
        }

        Usr user = new Usr();
        user.setUserName(userDTO.getUsername());
        user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        user.setEmail(userDTO.getEmail());
        user.setFullName(userDTO.getFullName());
        user.setRoles(userRoles);

        return userRepository.save(user);
    }

    @Override
    public Usr updateUser(Long id, UserRegistrationDTO userDTO) {
        logger.info("Updating user with id: {}", id);
        Usr user = getUserById(id);

        // Check if new username already exists (if changed)
        if (!user.getUserName().equals(userDTO.getUsername()) &&
            userRepository.existsByUserName(userDTO.getUsername())) {
            throw new IllegalArgumentException("Username already exists: " + userDTO.getUsername());
        }

        user.setUserName(userDTO.getUsername());
        user.setEmail(userDTO.getEmail());
        user.setFullName(userDTO.getFullName());

        if (userDTO.getPassword() != null && !userDTO.getPassword().trim().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        }

        return userRepository.save(user);
    }

    @Override
    public void deleteUser(Long id) {
        logger.info("Deleting user with id: {}", id);
        Usr user = getUserById(id);
        userRepository.delete(user);
    }

    @Override
    public BookBooking bookBook(String username, BookBookingDTO bookingDTO) {
        logger.info("User {} booking book with id: {}", username, bookingDTO.getBookId());

        Usr user = userRepository.findByUserName(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        Book book = bookRepository.findById(bookingDTO.getBookId())
                .orElseThrow(() -> new IllegalArgumentException("Book not found with id: " + bookingDTO.getBookId()));

        if (book.getQuantity() <= 0) {
            throw new IllegalStateException("Book is not available for booking");
        }

        // Check if user already has active booking for this book
        if (bookBookingRepository.existsByUserIdAndBookIdAndStatus(user.getId(), book.getId(), BookingStatus.ACTIVE)) {
            throw new IllegalStateException("User already has an active booking for this book");
        }

        // Decrease book quantity
        book.setQuantity(book.getQuantity() - 1);
        bookRepository.save(book);

        // Create booking
        BookBooking booking = new BookBooking();
        booking.setUser(user);
        booking.setBook(book);
        booking.setStatus(BookingStatus.ACTIVE);
        booking.setNotes(bookingDTO.getNotes());

        return bookBookingRepository.save(booking);
    }

    @Override
    public BookBooking returnBook(Long bookingId) {
        logger.info("Returning book for booking id: {}", bookingId);

        BookBooking booking = bookBookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found with id: " + bookingId));

        if (booking.getStatus() != BookingStatus.ACTIVE) {
            throw new IllegalStateException("Booking is not active");
        }

        // Update booking status
        booking.setStatus(BookingStatus.RETURNED);

        // Increase book quantity
        Book book = booking.getBook();
        book.setQuantity(book.getQuantity() + 1);
        bookRepository.save(book);

        return bookBookingRepository.save(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookBooking> getUserBookings(String username) {
        logger.debug("Fetching bookings for user: {}", username);
        Usr user = userRepository.findByUserName(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        return bookBookingRepository.findByUserWithUserAndBook(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookBooking> getActiveBookings(String username) {
        logger.debug("Fetching active bookings for user: {}", username);
        Usr user = userRepository.findByUserName(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        return bookBookingRepository.findByUserAndStatusWithUserAndBook(user, BookingStatus.ACTIVE);
    }
}
