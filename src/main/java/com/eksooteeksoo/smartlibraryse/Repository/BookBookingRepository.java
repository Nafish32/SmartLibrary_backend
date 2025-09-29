package com.eksooteeksoo.smartlibraryse.Repository;

import com.eksooteeksoo.smartlibraryse.Model.BookBooking;
import com.eksooteeksoo.smartlibraryse.Model.BookingStatus;
import com.eksooteeksoo.smartlibraryse.Model.Usr;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookBookingRepository extends JpaRepository<BookBooking, Long> {
    
    @Query("SELECT bb FROM BookBooking bb JOIN FETCH bb.user JOIN FETCH bb.book")
    List<BookBooking> findAllWithUserAndBook();
    
    @Query("SELECT bb FROM BookBooking bb JOIN FETCH bb.user JOIN FETCH bb.book WHERE bb.user = :user")
    List<BookBooking> findByUserWithUserAndBook(Usr user);
    
    @Query("SELECT bb FROM BookBooking bb JOIN FETCH bb.user JOIN FETCH bb.book WHERE bb.user = :user AND bb.status = :status")
    List<BookBooking> findByUserAndStatusWithUserAndBook(Usr user, BookingStatus status);
    
    List<BookBooking> findByUser(Usr user);
    List<BookBooking> findByStatus(BookingStatus status);
    List<BookBooking> findByUserAndStatus(Usr user, BookingStatus status);
    boolean existsByUserIdAndBookIdAndStatus(Long userId, Long bookId, BookingStatus status);
}
