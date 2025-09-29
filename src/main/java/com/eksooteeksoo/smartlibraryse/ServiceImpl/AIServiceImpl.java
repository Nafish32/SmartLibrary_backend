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

        // Priority 5: Year-based search (if no exact matches found)
        if (!foundExactMatches && (criteria.getYearFrom() != null || criteria.getYearTo() != null)) {
            List<Book> yearBooks = searchByYear(criteria.getYearFrom(), criteria.getYearTo());
            resultBooks.addAll(yearBooks);
        }

        // Priority 6: Genre search (only if no specific matches found and minimal other criteria)
        if (!foundExactMatches && criteria.getGenres() != null && !criteria.getGenres().isEmpty() &&
            (criteria.getTitles() == null || criteria.getTitles().isEmpty()) &&
            (criteria.getAuthors() == null || criteria.getAuthors().isEmpty())) {

            for (String genre : criteria.getGenres()) {
                List<Book> genreBooks = bookRepository.findByGenreContainingIgnoreCaseAndQuantityGreaterThan(genre, 0);
                resultBooks.addAll(genreBooks.stream().limit(5).toList()); // Limit genre results to avoid too many
            }
        }

        return new ArrayList<>(resultBooks);
    }

    // Multilingual author name mappings
    private final Map<String, List<String>> authorNameMappings = createAuthorNameMappings();

    private Map<String, List<String>> createAuthorNameMappings() {
        Map<String, List<String>> mappings = new HashMap<>();

        // Common Bangla authors and their English transliterations
        mappings.put("জে.কে. রোলিং", List.of("j.k. rowling", "jk rowling", "joanne rowling", "rowling"));
        mappings.put("রবীন্দ্রনাথ ঠাকুর", List.of("rabindranath tagore", "tagore", "rabindranath thakur"));
        mappings.put("কাজী নজরুল ইসলাম", List.of("kazi nazrul islam", "nazrul islam", "nazrul"));
        mappings.put("শরৎচন্দ্র চট্টোপাধ্যায়", List.of("sarat chandra chattopadhyay", "sharatchandra", "sarat chandra"));
        mappings.put("বঙ্কিমচন্দ্র চট্টোপাধ্যায়", List.of("bankim chandra chattopadhyay", "bankim chandra", "bankimchandra"));
        mappings.put("হুমায়ূন আহমেদ", List.of("humayun ahmed", "humayun ahmad"));
        mappings.put("আহমদ ছফা", List.of("ahmad sofa", "ahmed sofa"));
        mappings.put("জহির রায়হান", List.of("zahir raihan", "jahir raihan"));
        mappings.put("শহীদুল জহির", List.of("shahidul jahir", "shahidul zahir"));
        mappings.put("আল মাহমুদ", List.of("al mahmud", "al-mahmud"));
        mappings.put("আনিসুল হক", List.of("anisul hoque", "anisul haq"));

        // Add reverse mappings (English -> Bangla)
        Map<String, List<String>> reverseMappings = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : mappings.entrySet()) {
            String banglaName = entry.getKey();
            for (String englishVariation : entry.getValue()) {
                reverseMappings.computeIfAbsent(englishVariation.toLowerCase(), k -> new ArrayList<>()).add(banglaName);
            }
        }

        // Merge reverse mappings
        for (Map.Entry<String, List<String>> entry : reverseMappings.entrySet()) {
            mappings.put(entry.getKey(), entry.getValue());
        }

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
}
