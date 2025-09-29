package com.eksooteeksoo.smartlibraryse.Service;

import com.eksooteeksoo.smartlibraryse.DTO.BookBookingDTO;
import com.eksooteeksoo.smartlibraryse.DTO.UserRegistrationDTO;
import com.eksooteeksoo.smartlibraryse.Model.BookBooking;
import com.eksooteeksoo.smartlibraryse.Model.Usr;

import java.util.List;

public interface UserService {
    List<Usr> getAllUsers();
    Usr getUserById(Long id);
    Usr createUser(UserRegistrationDTO userDTO);
    Usr updateUser(Long id, UserRegistrationDTO userDTO);
    void deleteUser(Long id);
    BookBooking bookBook(String username, BookBookingDTO bookingDTO);
    BookBooking returnBook(Long bookingId);
    List<BookBooking> getUserBookings(String username);
    List<BookBooking> getActiveBookings(String username);
}
