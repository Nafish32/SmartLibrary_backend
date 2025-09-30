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
    private String genreSearchOperation; // "AND" or "OR"

    // Agent-like advanced search features
    private Boolean exactTitleMatch = false;
    private Boolean partialTitleMatch = true;
    private Boolean exactAuthorMatch = false;
    private Boolean partialAuthorMatch = true;
    private Integer maxResults = 50;
    private String sortBy = "relevance"; // "relevance", "title", "author", "year", "popularity"
    private String sortOrder = "desc"; // "asc" or "desc"
    private Boolean includeOutOfStock = false; // whether to include books with 0 quantity
    private Integer minYear;
    private Integer maxYear;
    private List<String> excludeGenres; // genres to exclude from results
    private List<String> excludeAuthors; // authors to exclude from results
    private String searchMode = "smart"; // "smart", "strict", "fuzzy"
    private Double relevanceThreshold = 0.3; // minimum relevance score (0.0 to 1.0)
    private Boolean prioritizeRecentBooks = false;
    private Boolean prioritizePopularBooks = false;
    private String language = "any"; // "english", "bengali", "any"
    private List<String> requiredKeywords; // keywords that MUST be present
    private List<String> optionalKeywords; // keywords that boost relevance if present
    private String userIntent = "general"; // "general", "specific", "browsing", "research"

    // Helper method to check if criteria is empty
    public boolean isEmpty() {
        return (titles == null || titles.isEmpty()) &&
               (authors == null || authors.isEmpty()) &&
               (genres == null || genres.isEmpty()) &&
               (keywords == null || keywords.isEmpty()) &&
               yearFrom == null && yearTo == null &&
               (isbn == null || isbn.trim().isEmpty()) &&
               (descriptionKeywords == null || descriptionKeywords.isEmpty()) &&
               (requiredKeywords == null || requiredKeywords.isEmpty()) &&
               (optionalKeywords == null || optionalKeywords.isEmpty());
    }

    // Helper method to determine search complexity
    public String getSearchComplexity() {
        int criteriaCount = 0;
        if (titles != null && !titles.isEmpty()) criteriaCount++;
        if (authors != null && !authors.isEmpty()) criteriaCount++;
        if (genres != null && !genres.isEmpty()) criteriaCount++;
        if (yearFrom != null || yearTo != null) criteriaCount++;
        if (isbn != null && !isbn.trim().isEmpty()) criteriaCount++;
        if (descriptionKeywords != null && !descriptionKeywords.isEmpty()) criteriaCount++;

        if (criteriaCount >= 4) return "complex";
        if (criteriaCount >= 2) return "moderate";
        return "simple";
    }
}
