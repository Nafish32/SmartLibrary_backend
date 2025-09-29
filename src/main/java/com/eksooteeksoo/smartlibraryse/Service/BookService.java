package com.eksooteeksoo.smartlibraryse.Service;

import com.eksooteeksoo.smartlibraryse.DTO.BookDTO;
import com.eksooteeksoo.smartlibraryse.Model.Book;

import java.util.List;

public interface BookService {
    List<Book> getAllBooks();
    List<Book> getAvailableBooks();
    Book getBookById(Long id);
    Book createBook(BookDTO bookDTO);
    Book updateBook(Long id, BookDTO bookDTO);
    void deleteBook(Long id);
    List<Book> searchBooks(String searchTerm);
    List<Book> searchAvailableBooks(String searchTerm);
}
