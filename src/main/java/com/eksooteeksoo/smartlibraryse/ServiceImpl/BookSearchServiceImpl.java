package com.eksooteeksoo.smartlibraryse.ServiceImpl;

import com.eksooteeksoo.smartlibraryse.DTO.BookSearchKeywords;
import com.eksooteeksoo.smartlibraryse.Model.Book;
import com.eksooteeksoo.smartlibraryse.Repository.BookRepository;
import com.eksooteeksoo.smartlibraryse.Service.BookSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class BookSearchServiceImpl implements BookSearchService {
    
    private static final Logger logger = LoggerFactory.getLogger(BookSearchServiceImpl.class);
    
    @Autowired
    private BookRepository bookRepository;
    
    // Multilingual author name mappings (Bangla -> English variations)
    private final Map<String, List<String>> authorNameMappings = createAuthorNameMappings();

    private Map<String, List<String>> createAuthorNameMappings() {
        Map<String, List<String>> mappings = new HashMap<>();

        // Common Bangla authors and their English transliterations
        mappings.put("জে.কে. রোলিং", Arrays.asList("j.k. rowling", "jk rowling", "joanne rowling", "rowling"));
        mappings.put("রবীন্দ্রনাথ ঠাকুর", Arrays.asList("rabindranath tagore", "tagore", "rabindranath thakur"));
        mappings.put("কাজী নজরুল ইসলাম", Arrays.asList("kazi nazrul islam", "nazrul islam", "nazrul"));
        mappings.put("শরৎচন্দ্র চট্টোপাধ্যায়", Arrays.asList("sarat chandra chattopadhyay", "sharatchandra", "sarat chandra"));
        mappings.put("বঙ্কিমচন্দ্র চট্টোপাধ্যায়", Arrays.asList("bankim chandra chattopadhyay", "bankim chandra", "bankimchandra"));
        mappings.put("মাইকেল মধুসূদন দত্ত", Arrays.asList("michael madhusudan dutt", "madhusudan dutt", "michael madhusudan"));
        mappings.put("তারাশঙ্কর বন্দ্যোপাধ্যায়", Arrays.asList("tarashankar bandyopadhyay", "tarashankar banerjee"));
        mappings.put("বিভূতিভূষণ বন্দ্যোপাধ্যায়", Arrays.asList("bibhutibhushan bandyopadhyay", "bibhutibhushan banerjee"));
        mappings.put("মানিক বন্দ্যোপাধ্যায়", Arrays.asList("manik bandyopadhyay", "manik banerjee"));
        mappings.put("হুমায়ূন আহমেদ", Arrays.asList("humayun ahmed", "humayun ahmad"));
        mappings.put("আহমদ ছফা", Arrays.asList("ahmad sofa", "ahmed sofa"));
        mappings.put("জহির রায়হান", Arrays.asList("zahir raihan", "jahir raihan"));
        mappings.put("শহীদুল জহির", Arrays.asList("shahidul jahir", "shahidul zahir"));
        mappings.put("আল মাহমুদ", Arrays.asList("al mahmud", "al-mahmud"));
        mappings.put("আনিসুল হক", Arrays.asList("anisul hoque", "anisul haq"));

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

    @Override
    public List<Book> searchBooks(BookSearchKeywords keywords) {
        logger.debug("Searching books with keywords: {}", keywords);
        
        Set<Book> results = new HashSet<>();
        
        // Use fallback search method directly since complex query was removed
        results.addAll(performFallbackSearch(keywords));
        
        // If no results found, try broader searches
        if (results.isEmpty()) {
            results.addAll(performBroaderSearch(keywords));
        }
        
        List<Book> finalResults = results.stream()
                .sorted((b1, b2) -> b2.getUpdatedAt().compareTo(b1.getUpdatedAt())) // Sort by most recent
                .collect(Collectors.toList());
        
        logger.debug("Found {} books matching the criteria", finalResults.size());
        return finalResults;
    }
    
    private Set<Book> performFallbackSearch(BookSearchKeywords keywords) {
        Set<Book> results = new HashSet<>();
        
        // Search by titles
        if (keywords.getTitles() != null && !keywords.getTitles().isEmpty()) {
            for (String title : keywords.getTitles()) {
                if (title != null && !title.trim().isEmpty()) {
                    results.addAll(bookRepository.findByTitleContainingIgnoreCaseAndQuantityGreaterThan(title, 0));
                }
            }
        }

        // Search by authors (with multilingual support)
        if (keywords.getAuthors() != null && !keywords.getAuthors().isEmpty()) {
            for (String author : keywords.getAuthors()) {
                if (author != null && !author.trim().isEmpty()) {
                    results.addAll(searchBooksByAuthorMultilingual(author));
                }
            }
        }

        // Search by genres
        if (keywords.getGenres() != null && !keywords.getGenres().isEmpty()) {
            for (String genre : keywords.getGenres()) {
                if (genre != null && !genre.trim().isEmpty()) {
                    results.addAll(bookRepository.findByGenreContainingIgnoreCaseAndQuantityGreaterThan(genre, 0));
                }
            }
        }

        // Search by year range
        if (keywords.getYearFrom() != null && keywords.getYearFrom() > 0) {
            if (keywords.getYearTo() != null && keywords.getYearTo() > 0) {
                // Search within year range
                results.addAll(bookRepository.findByPublishedYearBetweenAndQuantityGreaterThan(keywords.getYearFrom(), keywords.getYearTo(), 0));
            } else {
                // Search from year onwards
                results.addAll(bookRepository.findByPublishedYearBetweenAndQuantityGreaterThan(keywords.getYearFrom(), 2030, 0));
            }
        } else if (keywords.getYearTo() != null && keywords.getYearTo() > 0) {
            // Search up to year
            results.addAll(bookRepository.findByPublishedYearBetweenAndQuantityGreaterThan(1000, keywords.getYearTo(), 0));
        }

        return results;
    }

    private Set<Book> searchBooksByAuthorMultilingual(String authorQuery) {
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

        return results;
    }
    
    private Set<Book> performBroaderSearch(BookSearchKeywords keywords) {
        Set<Book> results = new HashSet<>();
        
        // If we have any keywords but no specific results, try general search
        List<String> allKeywords = new ArrayList<>();

        // Extract keywords from titles
        if (keywords.getTitles() != null && !keywords.getTitles().isEmpty()) {
            for (String title : keywords.getTitles()) {
                if (title != null && !title.trim().isEmpty()) {
                    allKeywords.addAll(Arrays.asList(title.split("\\s+")));
                }
            }
        }

        // Extract keywords from authors
        if (keywords.getAuthors() != null && !keywords.getAuthors().isEmpty()) {
            for (String author : keywords.getAuthors()) {
                if (author != null && !author.trim().isEmpty()) {
                    allKeywords.addAll(Arrays.asList(author.split("\\s+")));
                }
            }
        }

        // Extract keywords from genres
        if (keywords.getGenres() != null && !keywords.getGenres().isEmpty()) {
            for (String genre : keywords.getGenres()) {
                if (genre != null && !genre.trim().isEmpty()) {
                    allKeywords.addAll(Arrays.asList(genre.split("\\s+")));
                }
            }
        }

        // Also use general keywords if available
        if (keywords.getKeywords() != null && !keywords.getKeywords().isEmpty()) {
            for (String keyword : keywords.getKeywords()) {
                if (keyword != null && !keyword.trim().isEmpty()) {
                    allKeywords.add(keyword);
                }
            }
        }

        // Search with individual keywords
        for (String keyword : allKeywords) {
            if (keyword.length() > 2) { // Skip very short words
                results.addAll(bookRepository.searchAvailableBooks(keyword));

                // Also try multilingual author search for each keyword
                results.addAll(searchBooksByAuthorMultilingual(keyword));
            }
        }
        
        return results;
    }
}