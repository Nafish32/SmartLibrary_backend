package com.eksooteeksoo.smartlibraryse.DTO;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BookSearchCriteria {
    private List<String> titles;
    private List<String> authors;
    private List<String> genres;
    private List<String> keywords;
    private Integer yearFrom;
    private Integer yearTo;
    private String isbn;
    private List<String> descriptionKeywords;

    // Helper method to check if criteria is empty
    public boolean isEmpty() {
        return (titles == null || titles.isEmpty()) &&
               (authors == null || authors.isEmpty()) &&
               (genres == null || genres.isEmpty()) &&
               (keywords == null || keywords.isEmpty()) &&
               yearFrom == null && yearTo == null &&
               (isbn == null || isbn.trim().isEmpty()) &&
               (descriptionKeywords == null || descriptionKeywords.isEmpty());
    }
}
