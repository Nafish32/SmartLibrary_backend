package com.eksooteeksoo.smartlibraryse.ServiceImpl;

import com.eksooteeksoo.smartlibraryse.DTO.BookDTO;
import com.eksooteeksoo.smartlibraryse.Model.Book;
import com.eksooteeksoo.smartlibraryse.Repository.BookRepository;
import com.eksooteeksoo.smartlibraryse.Service.BookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BookServiceImpl implements BookService {

    private static final Logger logger = LoggerFactory.getLogger(BookServiceImpl.class);

    private final BookRepository bookRepository;

    public BookServiceImpl(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @Override
    public List<Book> getAllBooks() {
        logger.debug("Fetching all books");
        return bookRepository.findAll();
    }

    @Override
    public List<Book> getAvailableBooks() {
        logger.debug("Fetching available books");
        return bookRepository.findByQuantityGreaterThan(0);
    }

    @Override
    public Book getBookById(Long id) {
        logger.debug("Fetching book with id: {}", id);
        return bookRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Book not found with id: " + id));
    }

    @Override
    public Book createBook(BookDTO bookDTO) {
        logger.info("Creating new book: {}", bookDTO.getTitle());
        Book book = mapDtoToEntity(bookDTO);
        return bookRepository.save(book);
    }

    @Override
    public Book updateBook(Long id, BookDTO bookDTO) {
        logger.info("Updating book with id: {}", id);
        Book existingBook = getBookById(id);
        updateBookFromDto(existingBook, bookDTO);
        return bookRepository.save(existingBook);
    }

    @Override
    public void deleteBook(Long id) {
        logger.info("Deleting book with id: {}", id);
        Book book = getBookById(id);
        bookRepository.delete(book);
    }

    @Override
    public List<Book> searchBooks(String searchTerm) {
        logger.debug("Searching books with term: {}", searchTerm);
        return bookRepository.searchAvailableBooks(searchTerm);
    }

    @Override
    public List<Book> searchAvailableBooks(String searchTerm) {
        logger.debug("Searching available books with term: {}", searchTerm);
        return bookRepository.searchAvailableBooks(searchTerm);
    }

    private Book mapDtoToEntity(BookDTO bookDTO) {
        Book book = new Book();
        updateBookFromDto(book, bookDTO);
        return book;
    }

    private void updateBookFromDto(Book book, BookDTO bookDTO) {
        book.setTitle(bookDTO.getTitle());
        book.setAuthor(bookDTO.getAuthor());
        book.setPublishedYear(bookDTO.getPublishedYear());
        book.setQuantity(bookDTO.getQuantity());
        book.setIsbn(bookDTO.getIsbn());
        book.setGenre(bookDTO.getGenre());
        book.setDescription(bookDTO.getDescription());
    }
}
