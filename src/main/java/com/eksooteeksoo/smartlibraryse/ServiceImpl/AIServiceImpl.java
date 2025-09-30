package com.eksooteeksoo.smartlibraryse.ServiceImpl;

import com.eksooteeksoo.smartlibraryse.DTO.BookSearchCriteria;
import com.eksooteeksoo.smartlibraryse.DTO.ChatRequest;
import com.eksooteeksoo.smartlibraryse.DTO.ChatResponse;
import com.eksooteeksoo.smartlibraryse.Model.Book;
import com.eksooteeksoo.smartlibraryse.Repository.BookRepository;
import com.eksooteeksoo.smartlibraryse.Service.AIService;
import com.eksooteeksoo.smartlibraryse.Service.KeywordExtractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AIServiceImpl implements AIService {

    private static final Logger logger = LoggerFactory.getLogger(AIServiceImpl.class);

    private final BookRepository bookRepository;
    private final KeywordExtractionService keywordExtractionService;

    public AIServiceImpl(BookRepository bookRepository, KeywordExtractionService keywordExtractionService) {
        this.bookRepository = bookRepository;
        this.keywordExtractionService = keywordExtractionService;
    }

    @Override
    public ChatResponse processChat(ChatRequest chatRequest) {
        try {
            String message = chatRequest.getMessage();
            String language = chatRequest.getLanguage() != null ? chatRequest.getLanguage() : "bn+en";
            
            logger.info("Processing chat message: '{}' in language: {}", message, language);

            // Extract search criteria using Mistral AI or rule-based fallback
            BookSearchCriteria searchCriteria = keywordExtractionService.extractBookSearchCriteria(message);
            logger.info("Extracted search criteria: {}", searchCriteria);

            // Search for books using the extracted criteria
            List<Book> foundBooks = searchBooksWithCriteria(searchCriteria);

            // Generate response message
            String responseMessage = generateResponseMessage(foundBooks, searchCriteria, language);

            // Return response with books
            if (!foundBooks.isEmpty()) {
                return new ChatResponse(foundBooks, responseMessage);
            } else {
                return new ChatResponse(responseMessage);
            }
            
        } catch (Exception e) {
            logger.error("Error processing chat request", e);
            return ChatResponse.error(getErrorMessage(chatRequest.getLanguage()));
        }
    }

    private List<Book> searchBooksWithCriteria(BookSearchCriteria criteria) {
        Set<Book> resultBooks = new HashSet<>();
        boolean foundExactMatches = false;

        if (criteria.isEmpty()) {
            // If no specific criteria, return some available books
            return bookRepository.findByQuantityGreaterThan(0).stream().limit(10).toList();
        }

        // Priority 1: Combined title + description search for specific queries like "third book in harry potter series"
        if (criteria.getTitles() != null && !criteria.getTitles().isEmpty() &&
            criteria.getDescriptionKeywords() != null && !criteria.getDescriptionKeywords().isEmpty()) {

            for (String title : criteria.getTitles()) {
                for (String descKeyword : criteria.getDescriptionKeywords()) {
                    // Search for books that match both title and description criteria
                    List<Book> combinedBooks = bookRepository.findByTitleContainingIgnoreCaseAndDescriptionContainingIgnoreCaseAndQuantityGreaterThan(title, descKeyword, 0);
                    if (!combinedBooks.isEmpty()) {
                        resultBooks.addAll(combinedBooks);
                        foundExactMatches = true;
                    }
                }
            }
        }

        // Priority 2: Exact title matches (if no combined matches found)
        if (!foundExactMatches && criteria.getTitles() != null && !criteria.getTitles().isEmpty()) {
            for (String title : criteria.getTitles()) {
                List<Book> exactTitleBooks = bookRepository.findByTitleContainingIgnoreCaseAndQuantityGreaterThan(title, 0);
                if (!exactTitleBooks.isEmpty()) {
                    resultBooks.addAll(exactTitleBooks);
                    foundExactMatches = true;
                }
            }
        }

        // Priority 3: Description keyword search (only if no title matches and description keywords exist)
        if (!foundExactMatches && criteria.getDescriptionKeywords() != null && !criteria.getDescriptionKeywords().isEmpty()) {
            for (String descKeyword : criteria.getDescriptionKeywords()) {
                List<Book> descBooks = bookRepository.findByDescriptionContainingIgnoreCaseAndQuantityGreaterThan(descKeyword, 0);
                if (!descBooks.isEmpty()) {
                    resultBooks.addAll(descBooks);
                    foundExactMatches = true;
                }
            }
        }

        // If we found exact matches, apply additional filters to existing results
        if (foundExactMatches && !resultBooks.isEmpty()) {
            List<Book> filteredResults = new ArrayList<>(resultBooks);

            // Apply year filter if specified
            if (criteria.getYearFrom() != null || criteria.getYearTo() != null) {
                filteredResults = filteredResults.stream()
                        .filter(book -> {
                            if (criteria.getYearFrom() != null && book.getPublishedYear() < criteria.getYearFrom()) {
                                return false;
                            }
                            return criteria.getYearTo() == null || book.getPublishedYear() <= criteria.getYearTo();
                        })
                        .toList();
            }

            // Apply multilingual author filter if specified
            if (criteria.getAuthors() != null && !criteria.getAuthors().isEmpty()) {
                filteredResults = filteredResults.stream()
                        .filter(book -> matchesMultilingualAuthor(book.getAuthor(), criteria.getAuthors()))
                        .toList();
            }

            return filteredResults;
        }

        // Priority 4: Multilingual author search (if no exact title/description matches)
        if (criteria.getAuthors() != null && !criteria.getAuthors().isEmpty()) {
            for (String author : criteria.getAuthors()) {
                List<Book> authorBooks = searchBooksByAuthorMultilingual(author);
                resultBooks.addAll(authorBooks);
                foundExactMatches = true;
            }
        }

        // Priority 5: Combined Genre + Year search (most common use case)
        if (!foundExactMatches && criteria.getGenres() != null && !criteria.getGenres().isEmpty()) {
            String operation = criteria.getGenreSearchOperation();
            List<Book> genreBooks;

            if ("AND".equalsIgnoreCase(operation)) {
                // User wants books that contain ALL specified genres
                genreBooks = searchBooksWithAllGenres(criteria.getGenres());
                logger.info("Using AND logic for genres: {}", criteria.getGenres());
            } else {
                // User wants books that contain ANY of the specified genres (OR logic)
                genreBooks = searchBooksWithAnyGenre(criteria.getGenres());
                logger.info("Using OR logic for genres: {}", criteria.getGenres());
            }

            // Apply year filter to genre results if specified
            if (criteria.getYearFrom() != null || criteria.getYearTo() != null) {
                genreBooks = genreBooks.stream()
                        .filter(book -> {
                            if (criteria.getYearFrom() != null && book.getPublishedYear() < criteria.getYearFrom()) {
                                return false;
                            }
                            return criteria.getYearTo() == null || book.getPublishedYear() <= criteria.getYearTo();
                        })
                        .toList();
            }

            resultBooks.addAll(genreBooks);
            foundExactMatches = true;
        }

        // Priority 6: Year-only search (only if no genres specified)
        if (!foundExactMatches && (criteria.getYearFrom() != null || criteria.getYearTo() != null)) {
            List<Book> yearBooks = searchByYear(criteria.getYearFrom(), criteria.getYearTo());
            resultBooks.addAll(yearBooks);
        }

        return new ArrayList<>(resultBooks);
    }

    // Multilingual author name mappings - Enhanced with both directions
    private final Map<String, List<String>> authorNameMappings = createAuthorNameMappings();

    private Map<String, List<String>> createAuthorNameMappings() {
        Map<String, List<String>> mappings = new HashMap<>();

        // Common Bangla authors and their English transliterations
        mappings.put("জে.কে. রোলিং", List.of("j.k. rowling", "jk rowling", "joanne rowling", "rowling"));
        mappings.put("j.k. rowling", List.of("জে.কে. রোলিং", "জোয়ান রোলিং"));

        mappings.put("রবীন্দ্রনাথ ঠাকুর", List.of("rabindranath tagore", "tagore", "rabindranath thakur", "রবি ঠাকুর", "রবীন্দ্রনাথ"));
        mappings.put("rabindranath tagore", List.of("রবীন্দ্রনাথ ঠাকুর", "রবি ঠাকুর"));
        mappings.put("রবি", List.of("rabindranath tagore", "রবীন্দ্রনাথ ঠাকুর", "রবি ঠাকুর"));

        mappings.put("কাজী নজরুল ইসলাম", List.of("kazi nazrul islam", "nazrul islam", "nazrul", "নজরুল"));
        mappings.put("nazrul", List.of("কাজী নজরুল ইসলাম", "নজরুল"));
        mappings.put("নজরুল", List.of("kazi nazrul islam", "nazrul"));

        mappings.put("শরৎচন্দ্র চট্টোপাধ্যায়", List.of("sarat chandra chattopadhyay", "sharatchandra", "sarat chandra", "শরৎচন্দ্র"));
        mappings.put("sarat chandra", List.of("শরৎচন্দ্র চট্টোপাধ্যায়", "শরৎচন্দ্র"));
        mappings.put("শরৎ", List.of("sarat chandra chattopadhyay", "শরৎচন্দ্র চট্টোপাধ্যায়"));

        mappings.put("বঙ্কিমচন্দ্র চট্টোপাধ্যায়", List.of("bankim chandra chattopadhyay", "bankim chandra", "bankimchandra", "বঙ্কিম"));
        mappings.put("bankim chandra", List.of("বঙ্কিমচন্দ্র চট্টোপাধ্যায়", "বঙ্কিম"));
        mappings.put("বঙ্কিম", List.of("bankim chandra chattopadhyay", "বঙ্কিমচন্দ্র চট্টোপাধ্যায়"));

        mappings.put("হুমায়ূন আহমেদ", List.of("humayun ahmed", "humayun ahmad", "হুমায়ূন"));
        mappings.put("humayun ahmed", List.of("হুমায়ূন আহমেদ", "হুমায়ূন"));
        mappings.put("হুমায়ূন", List.of("humayun ahmed", "হুমায়ূন আহমেদ"));

        mappings.put("আহমদ ছফা", List.of("ahmad sofa", "ahmed sofa"));
        mappings.put("ahmad sofa", List.of("আহমদ ছফা"));

        mappings.put("জহির রায়হান", List.of("zahir raihan", "jahir raihan"));
        mappings.put("zahir raihan", List.of("জহির রায়হান"));

        mappings.put("শহীদুল জহির", List.of("shahidul jahir", "shahidul zahir"));
        mappings.put("shahidul jahir", List.of("শহীদুল জহির"));

        mappings.put("আল মাহমুদ", List.of("al mahmud", "al-mahmud"));
        mappings.put("al mahmud", List.of("আল মাহমুদ"));

        mappings.put("আনিসুল হক", List.of("anisul hoque", "anisul haq"));
        mappings.put("anisul hoque", List.of("আনিসুল হক"));

        return mappings;
    }

    private List<Book> searchBooksByAuthorMultilingual(String authorQuery) {
        Set<Book> results = new HashSet<>();
        String queryLower = authorQuery.toLowerCase().trim();

        // Direct search first
        results.addAll(bookRepository.findByAuthorContainingIgnoreCaseAndQuantityGreaterThan(authorQuery, 0));

        // Search using author name mappings
        Set<String> authorVariations = new HashSet<>();
        authorVariations.add(queryLower);

        // Find all possible variations of the author name
        for (Map.Entry<String, List<String>> entry : authorNameMappings.entrySet()) {
            String key = entry.getKey().toLowerCase();
            List<String> variations = entry.getValue();

            // If query matches any key or variation, add all variations
            if (key.contains(queryLower) || queryLower.contains(key) ||
                variations.stream().anyMatch(v -> v.toLowerCase().contains(queryLower) || queryLower.contains(v.toLowerCase()))) {

                authorVariations.add(entry.getKey()); // Add the key (could be Bangla or English)
                authorVariations.addAll(variations.stream().map(String::toLowerCase).collect(Collectors.toList()));
            }
        }

        // Search for each variation
        for (String variation : authorVariations) {
            if (!variation.trim().isEmpty()) {
                results.addAll(bookRepository.findByAuthorContainingIgnoreCaseAndQuantityGreaterThan(variation, 0));
            }
        }

        logger.debug("Found {} author variations for query '{}': {}", authorVariations.size(), authorQuery, authorVariations);

        return new ArrayList<>(results);
    }

    private boolean matchesMultilingualAuthor(String bookAuthor, List<String> searchAuthors) {
        String bookAuthorLower = bookAuthor.toLowerCase();

        for (String searchAuthor : searchAuthors) {
            String searchAuthorLower = searchAuthor.toLowerCase();

            // Direct match
            if (bookAuthorLower.contains(searchAuthorLower) || searchAuthorLower.contains(bookAuthorLower)) {
                return true;
            }

            // Check through multilingual mappings
            for (Map.Entry<String, List<String>> entry : authorNameMappings.entrySet()) {
                String key = entry.getKey().toLowerCase();
                List<String> variations = entry.getValue();

                // If book author matches key or any variation
                boolean bookAuthorMatches = key.contains(bookAuthorLower) || bookAuthorLower.contains(key) ||
                    variations.stream().anyMatch(v -> v.toLowerCase().contains(bookAuthorLower) || bookAuthorLower.contains(v.toLowerCase()));

                // If search author matches key or any variation
                boolean searchAuthorMatches = key.contains(searchAuthorLower) || searchAuthorLower.contains(key) ||
                    variations.stream().anyMatch(v -> v.toLowerCase().contains(searchAuthorLower) || searchAuthorLower.contains(v.toLowerCase()));

                if (bookAuthorMatches && searchAuthorMatches) {
                    return true;
                }
            }
        }

        return false;
    }

    private List<Book> searchByYear(Integer yearFrom, Integer yearTo) {
        if (yearFrom != null && yearTo != null) {
            if (yearFrom.equals(yearTo)) {
                return bookRepository.findByPublishedYearAndQuantityGreaterThan(yearFrom, 0);
            } else {
                return bookRepository.findByPublishedYearBetweenAndQuantityGreaterThan(yearFrom, yearTo, 0);
            }
        } else if (yearFrom != null) {
            return bookRepository.findByPublishedYearBetweenAndQuantityGreaterThan(yearFrom, 2030, 0);
        } else if (yearTo != null) {
            return bookRepository.findByPublishedYearBetweenAndQuantityGreaterThan(1000, yearTo, 0);
        }
        return new ArrayList<>();
    }

    private String generateResponseMessage(List<Book> books, BookSearchCriteria criteria, String language) {
        if (books.isEmpty()) {
            return getNoResultsMessage(language, criteria);
        }

        String bookCount = String.valueOf(books.size());
        String searchSummary = generateSearchSummary(criteria, language);

        return getLanguageResponse(language,
            searchSummary.isEmpty() ?
                "আপনার অনুসন্ধানের সাথে " + bookCount + "টি বই পাওয়া গেছে:" :
                searchSummary + " এর জন্য " + bookCount + "টি বই পাওয়া গেছে:",
            searchSummary.isEmpty() ?
                "Found " + bookCount + " books matching your search:" :
                "Found " + bookCount + " books for " + searchSummary + ":",
            searchSummary.isEmpty() ?
                "পাওয়া গেছে " + bookCount + "টি বই:" :
                searchSummary + " এর জন্য পাওয়া গেছে " + bookCount + "টি বই:");
    }

    private String generateSearchSummary(BookSearchCriteria criteria, String language) {
        List<String> summaryParts = new ArrayList<>();

        if (criteria.getYearFrom() != null || criteria.getYearTo() != null) {
            if (criteria.getYearFrom() != null && criteria.getYearTo() != null) {
                if (criteria.getYearFrom().equals(criteria.getYearTo())) {
                    summaryParts.add(getLanguageResponse(language,
                        criteria.getYearFrom() + " সালের বই",
                        "books from " + criteria.getYearFrom(),
                        criteria.getYearFrom() + " সালের books"));
                } else {
                    summaryParts.add(getLanguageResponse(language,
                        criteria.getYearFrom() + "-" + criteria.getYearTo() + " সালের বই",
                        "books from " + criteria.getYearFrom() + "-" + criteria.getYearTo(),
                        criteria.getYearFrom() + "-" + criteria.getYearTo() + " সালের books"));
                }
            } else if (criteria.getYearFrom() != null) {
                summaryParts.add(getLanguageResponse(language,
                    criteria.getYearFrom() + " সালের পরের বই",
                    "books after " + criteria.getYearFrom(),
                    criteria.getYearFrom() + " সালের পরের books"));
            } else {
                summaryParts.add(getLanguageResponse(language,
                    criteria.getYearTo() + " সালের আগের বই",
                    "books before " + criteria.getYearTo(),
                    criteria.getYearTo() + " সালের আগের books"));
            }
        }

        if (criteria.getGenres() != null && !criteria.getGenres().isEmpty()) {
            String genres = String.join(", ", criteria.getGenres());
            summaryParts.add(getLanguageResponse(language,
                genres + " ধরনের বই",
                genres + " books",
                genres + " genre এর books"));
        }

        if (criteria.getAuthors() != null && !criteria.getAuthors().isEmpty()) {
            String authors = String.join(", ", criteria.getAuthors());
            summaryParts.add(getLanguageResponse(language,
                authors + " এর বই",
                "books by " + authors,
                authors + " এর books"));
        }

        return String.join(", ", summaryParts);
    }

    private String getNoResultsMessage(String language, BookSearchCriteria criteria) {
        String searchSummary = generateSearchSummary(criteria, language);

        if (searchSummary.isEmpty()) {
            return getLanguageResponse(language,
                "দুঃখিত, আপনার অনুসন্ধানের সাথে মিলে যাওয়া কোনো বই পাওয়া যায়নি। অন্য কিছু খুঁজে দেখুন।",
                "Sorry, no books found matching your search. Try searching for something else.",
                "দুঃখিত, কোনো বই পাওয়া যায়নি। অন্য কিছু search করে দেখুন।");
        } else {
            return getLanguageResponse(language,
                searchSummary + " এর জন্য কোনো বই পাওয়া যায়নি। অন্য কিছু খুঁজে দেখুন।",
                "No books found for " + searchSummary + ". Try searching for something else.",
                searchSummary + " এর জন্য কোনো books পাওয়া যায়নি। অন্য কিছু try করুন।");
        }
    }
    
    private String getLanguageResponse(String language, String banglaText, String englishText, String mixedText) {
        if ("bn".equals(language)) {
            return banglaText;
        } else if ("en".equals(language)) {
            return englishText;
        } else {
            return mixedText;
        }
    }

    private String getErrorMessage(String language) {
        return getLanguageResponse(language,
            "দুঃখিত, কিছু সমস্যা হয়েছে। আবার চেষ্টা করুন।",
            "Sorry, something went wrong. Please try again.",
            "দুঃখিত, কিছু problem হয়েছে। আবার try করুন।");
    }

    /**
     * Search for books that contain ALL specified genres (AND logic)
     * This method ensures that returned books have all the genres mentioned in the search
     */
    private List<Book> searchBooksWithAllGenres(List<String> genres) {
        if (genres == null || genres.isEmpty()) {
            return new ArrayList<>();
        }

        // Start with books that contain the first genre
        List<Book> candidateBooks = bookRepository.findByGenreContainingIgnoreCaseAndQuantityGreaterThan(genres.get(0), 0);

        // Filter books to only those that contain ALL specified genres
        return candidateBooks.stream()
                .filter(book -> {
                    String bookGenre = book.getGenre();
                    if (bookGenre == null) return false;

                    String bookGenreLower = bookGenre.toLowerCase();

                    // Check if ALL genres are present in this book's genre field
                    return genres.stream().allMatch(genre ->
                        bookGenreLower.contains(genre.toLowerCase())
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * Search for books that contain ANY of the specified genres (OR logic)
     * This method ensures that returned books have at least one of the genres mentioned in the search
     */
    private List<Book> searchBooksWithAnyGenre(List<String> genres) {
        if (genres == null || genres.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Book> resultBooks = new HashSet<>();

        // Find books that contain ANY of the specified genres
        for (String genre : genres) {
            List<Book> genreBooks = bookRepository.findByGenreContainingIgnoreCaseAndQuantityGreaterThan(genre, 0);
            resultBooks.addAll(genreBooks);
        }

        return new ArrayList<>(resultBooks);
    }
}
