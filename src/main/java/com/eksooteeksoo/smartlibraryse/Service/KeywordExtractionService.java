package com.eksooteeksoo.smartlibraryse.Service;

import com.eksooteeksoo.smartlibraryse.DTO.BookSearchCriteria;

public interface KeywordExtractionService {
    BookSearchCriteria extractBookSearchCriteria(String userMessage);
}
