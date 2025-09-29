package com.eksooteeksoo.smartlibraryse.Service;

import com.eksooteeksoo.smartlibraryse.DTO.BookSearchKeywords;
import com.eksooteeksoo.smartlibraryse.Model.Book;

import java.util.List;

public interface BookSearchService {
    List<Book> searchBooks(BookSearchKeywords keywords);
}