package com.eksooteeksoo.smartlibraryse.DTO;

import lombok.Data;
import java.util.List;

@Data
public class BookSearchKeywords {
    private List<String> titles;
    private List<String> authors;
    private List<String> genres;
    private Integer yearFrom;
    private Integer yearTo;
    private List<String> keywords; // General keywords from description
    private String isbn;
    
    public BookSearchKeywords() {
        // Default constructor
    }
}