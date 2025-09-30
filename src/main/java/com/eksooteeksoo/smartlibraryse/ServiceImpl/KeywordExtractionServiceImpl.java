package com.eksooteeksoo.smartlibraryse.ServiceImpl;

import com.eksooteeksoo.smartlibraryse.DTO.BookSearchCriteria;
import com.eksooteeksoo.smartlibraryse.Service.KeywordExtractionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class KeywordExtractionServiceImpl implements KeywordExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(KeywordExtractionServiceImpl.class);
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${mistral.api.key:}")
    private String mistralApiKey;

    @Value("${mistral.api.url:https://api.mistral.ai/v1/chat/completions}")
    private String mistralApiUrl;

    public KeywordExtractionServiceImpl() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public BookSearchCriteria extractBookSearchCriteria(String userMessage) {
        try {
            // First try Mistral AI if API key is available
            if (mistralApiKey != null && !mistralApiKey.trim().isEmpty()) {
                BookSearchCriteria aiExtracted = extractWithMistralAI(userMessage);
                if (aiExtracted != null) {
                    return aiExtracted;
                }
            }

            // Fallback to rule-based extraction
            return extractWithRules(userMessage);

        } catch (Exception e) {
            logger.error("Error extracting keywords, falling back to rule-based extraction", e);
            return extractWithRules(userMessage);
        }
    }

    private BookSearchCriteria extractWithMistralAI(String userMessage) {
        try {
            String prompt = buildPrompt(userMessage);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "mistral-tiny");
            requestBody.put("max_tokens", 300);
            requestBody.put("temperature", 0.1); // Lower temperature for more precise extraction

            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", prompt);
            messages.add(message);
            requestBody.put("messages", messages);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + mistralApiKey);
            headers.set("Content-Type", "application/json");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                mistralApiUrl, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                return parseAIResponse(response.getBody());
            }

        } catch (Exception e) {
            logger.warn("Mistral AI extraction failed, using fallback: {}", e.getMessage());
        }

        return null;
    }

    private String buildPrompt(String userMessage) {
        return """
            You are an advanced book search AI agent. Extract comprehensive book search criteria from the user message. 
            Respond ONLY with a JSON object containing these fields:
            {
                "titles": [exact book titles or series names mentioned - be very specific],
                "authors": [exact author names mentioned in ANY language including Bengali/Bangla],
                "genres": [specific genres like fiction, romance, mystery, etc.],
                "keywords": [other relevant keywords for general search],
                "yearFrom": number or null (earliest publication year),
                "yearTo": number or null (latest publication year),
                "isbn": "string or null",
                "descriptionKeywords": [specific keywords that should be searched in book descriptions],
                "genreSearchOperation": "AND" or "OR" (determine user intent for multiple genres),
                "exactTitleMatch": boolean (true if user wants exact title match),
                "partialTitleMatch": boolean (true if partial matching is acceptable),
                "exactAuthorMatch": boolean (true if user wants exact author match),
                "partialAuthorMatch": boolean (true if partial matching is acceptable),
                "maxResults": number (1-100, default 50),
                "sortBy": "relevance" | "title" | "author" | "year" | "popularity",
                "sortOrder": "asc" | "desc",
                "includeOutOfStock": boolean (whether to include books with 0 quantity),
                "excludeGenres": [genres to exclude from results],
                "excludeAuthors": [authors to exclude from results],
                "searchMode": "smart" | "strict" | "fuzzy",
                "prioritizeRecentBooks": boolean,
                "prioritizePopularBooks": boolean,
                "language": "english" | "bengali" | "any",
                "requiredKeywords": [keywords that MUST be present],
                "optionalKeywords": [keywords that boost relevance if present],
                "userIntent": "general" | "specific" | "browsing" | "research"
            }

            ADVANCED AGENT RULES:
            
            1. INTENT DETECTION:
               - "specific": User looking for exact book/author (use exactMatch=true)
               - "browsing": User exploring options (use fuzzy search, higher maxResults)
               - "research": Academic/detailed search (prioritize recent books, strict mode)
               - "general": Default casual search
            
            2. SEARCH MODE DETECTION:
               - "strict": User uses quotes, says "exactly", "precisely"
               - "fuzzy": User says "similar", "like", "something like"
               - "smart": Default intelligent search
            
            3. EDGE CASE HANDLING:
               - Ambiguous titles: Set partialTitleMatch=true, add title variations to optionalKeywords
               - Multiple authors: Check if user wants books by ALL authors (AND) or ANY author (OR)
               - Typos/misspellings: Use fuzzy mode, add corrected terms to optionalKeywords
               - Incomplete information: Fill gaps with smart defaults based on context
               - Contradictory requests: Prioritize most recent/specific instruction
               
            4. QUANTITY & SORTING:
               - If user asks for "few" books: maxResults=10
               - If user asks for "many" books: maxResults=100
               - If user asks for "latest" books: sortBy="year", sortOrder="desc", prioritizeRecentBooks=true
               - If user asks for "popular" books: sortBy="popularity", prioritizePopularBooks=true, maxResults=10
               - If user asks for "best" books: sortBy="relevance", prioritizePopularBooks=true
               
            5. EXCLUSION HANDLING:
               - "not romance": add "romance" to excludeGenres
               - "except John Doe": add "John Doe" to excludeAuthors  
               - "avoid": add terms to appropriate exclude arrays
               
            6. LANGUAGE DETECTION:
               - Detect predominant language in query
               - If mixed language, set language="any"
               - If only English terms: language="english" 
               - If only Bengali terms: language="bengali"
               
            7. AVAILABILITY PREFERENCES:
               - Default: includeOutOfStock=false (only available books)
               - If user says "any books" or "all books": includeOutOfStock=true
               - If user says "available now": includeOutOfStock=false (explicit)
               
            8. COMPOUND QUERIES:
               - Break down complex multi-part queries
               - Identify primary vs secondary criteria
               - Use requiredKeywords for primary, optionalKeywords for secondary
               
            9. TEMPORAL CONTEXT:
               - "recent": last 5 years, prioritizeRecentBooks=true
               - "classic": before 1980, sortBy="year", sortOrder="asc"
               - "modern": after 2000
               - "contemporary": after 2010

            GENRE SEARCH OPERATION RULES:
            - Use "AND" when user says: "both X and Y", "X and Y", "with both", "containing all", "must have both"
            - Use "OR" when user says: "X or Y", "either X or Y", "X অথবা Y", "any of these", "one of"
            - Default to "OR" if unclear or single genre mentioned
            - Bengali AND indicators: "এবং", "আর", "ও", "উভয়", "দুটোই"
            - Bengali OR indicators: "অথবা", "বা", "কিংবা", "যেকোনো"

            EXAMPLE EDGE CASES:
            
            "I want the exact book 'To Kill a Mockingbird'" →
            exactTitleMatch: true, userIntent: "specific", searchMode: "strict"
            
            "Show me something like Harry Potter but not fantasy" →
            partialTitleMatch: true, searchMode: "fuzzy", excludeGenres: ["fantasy"]
            
            "Latest popular science fiction books, at least 10" →
            genres: ["science-fiction"], sortBy: "year", prioritizeRecentBooks: true, prioritizePopularBooks: true, maxResults: 10
            
            "Books by Rabindranath Tagore or রবীন্দ্রনাথ ঠাকুর except poetry" →
            authors: ["Rabindranath Tagore", "রবীন্দ্রনাথ ঠাকুর"], excludeGenres: ["poetry"], language: "any"
            
            "I need research materials on AI, published after 2020, available now" →
            keywords: ["AI", "artificial intelligence"], yearFrom: 2020, userIntent: "research", includeOutOfStock: false, prioritizeRecentBooks: true

            User message: """ + userMessage;
    }

    private BookSearchCriteria parseAIResponse(String aiResponse) {
        try {
            JsonNode rootNode = objectMapper.readTree(aiResponse);
            JsonNode choicesNode = rootNode.get("choices");

            if (choicesNode != null && choicesNode.isArray() && choicesNode.size() > 0) {
                JsonNode messageNode = choicesNode.get(0).get("message");
                if (messageNode != null) {
                    String content = messageNode.get("content").asText();

                    // Clean up the response - extract JSON from the content
                    String jsonContent = extractJsonFromText(content);

                    JsonNode criteriaNode = objectMapper.readTree(jsonContent);

                    return BookSearchCriteria.builder()
                        .titles(extractStringList(criteriaNode, "titles"))
                        .authors(extractStringList(criteriaNode, "authors"))
                        .genres(extractStringList(criteriaNode, "genres"))
                        .keywords(extractStringList(criteriaNode, "keywords"))
                        .yearFrom(extractInteger(criteriaNode, "yearFrom"))
                        .yearTo(extractInteger(criteriaNode, "yearTo"))
                        .isbn(extractString(criteriaNode, "isbn"))
                        .descriptionKeywords(extractStringList(criteriaNode, "descriptionKeywords"))
                        .genreSearchOperation(extractString(criteriaNode, "genreSearchOperation"))
                        // Advanced agent-like fields
                        .exactTitleMatch(extractBoolean(criteriaNode, "exactTitleMatch"))
                        .partialTitleMatch(extractBoolean(criteriaNode, "partialTitleMatch"))
                        .exactAuthorMatch(extractBoolean(criteriaNode, "exactAuthorMatch"))
                        .partialAuthorMatch(extractBoolean(criteriaNode, "partialAuthorMatch"))
                        .maxResults(extractInteger(criteriaNode, "maxResults"))
                        .sortBy(extractString(criteriaNode, "sortBy"))
                        .sortOrder(extractString(criteriaNode, "sortOrder"))
                        .includeOutOfStock(extractBoolean(criteriaNode, "includeOutOfStock"))
                        .excludeGenres(extractStringList(criteriaNode, "excludeGenres"))
                        .excludeAuthors(extractStringList(criteriaNode, "excludeAuthors"))
                        .searchMode(extractString(criteriaNode, "searchMode"))
                        .prioritizeRecentBooks(extractBoolean(criteriaNode, "prioritizeRecentBooks"))
                        .prioritizePopularBooks(extractBoolean(criteriaNode, "prioritizePopularBooks"))
                        .language(extractString(criteriaNode, "language"))
                        .requiredKeywords(extractStringList(criteriaNode, "requiredKeywords"))
                        .optionalKeywords(extractStringList(criteriaNode, "optionalKeywords"))
                        .userIntent(extractString(criteriaNode, "userIntent"))
                        .build();
                }
            }
        } catch (Exception e) {
            logger.error("Error parsing AI response: {}", e.getMessage());
        }

        return null;
    }

    private String extractJsonFromText(String text) {
        // Find JSON object in the text
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}') + 1;

        if (start >= 0 && end > start) {
            return text.substring(start, end);
        }

        return text;
    }

    private List<String> extractStringList(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.get(fieldName);
        List<String> result = new ArrayList<>();

        if (fieldNode != null && fieldNode.isArray()) {
            for (JsonNode item : fieldNode) {
                if (item.isTextual() && !item.asText().trim().isEmpty()) {
                    result.add(item.asText().trim());
                }
            }
        }

        return result;
    }

    private Integer extractInteger(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.get(fieldName);
        if (fieldNode != null && fieldNode.isNumber()) {
            return fieldNode.asInt();
        }
        return null;
    }

    private String extractString(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.get(fieldName);
        if (fieldNode != null && fieldNode.isTextual() && !fieldNode.asText().trim().isEmpty()) {
            return fieldNode.asText().trim();
        }
        return null;
    }

    private Boolean extractBoolean(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.get(fieldName);
        if (fieldNode != null && fieldNode.isBoolean()) {
            return fieldNode.asBoolean();
        }
        return null;
    }

    private BookSearchCriteria extractWithRules(String userMessage) {
        String cleanMessage = userMessage.toLowerCase().trim();

        BookSearchCriteria.BookSearchCriteriaBuilder builder = BookSearchCriteria.builder();

        // Extract years using regex
        extractYears(cleanMessage, builder);

        // Extract common genres
        extractGenres(cleanMessage, builder);

        // Detect genre search operation (AND vs OR)
        String genreOperation = detectGenreSearchOperation(userMessage);
        builder.genreSearchOperation(genreOperation);

        // Extract general keywords
        List<String> keywords = extractGeneralKeywords(cleanMessage);
        builder.keywords(keywords);

        // Use same keywords for description search
        builder.descriptionKeywords(new ArrayList<>(keywords));

        // Agent-like enhancements for rule-based extraction
        detectUserIntent(userMessage, builder);
        detectSearchMode(userMessage, builder);
        detectSortingPreferences(userMessage, builder);
        detectExclusions(userMessage, builder);
        detectLanguagePreference(userMessage, builder);
        detectAvailabilityPreferences(userMessage, builder);
        detectQuantityPreferences(userMessage, builder);

        return builder.build();
    }

    /**
     * Detect user intent from natural language patterns
     */
    private void detectUserIntent(String userMessage, BookSearchCriteria.BookSearchCriteriaBuilder builder) {
        String message = userMessage.toLowerCase();

        // Specific intent indicators
        if (message.contains("exact") || message.contains("precisely") ||
            message.contains("specific") || message.matches(".*\".*\".*")) {
            builder.userIntent("specific");
            builder.exactTitleMatch(true);
            builder.exactAuthorMatch(true);
            builder.searchMode("strict");
        }
        // Research intent indicators
        else if (message.contains("research") || message.contains("study") ||
                 message.contains("academic") || message.contains("paper") ||
                 message.contains("thesis")) {
            builder.userIntent("research");
            builder.prioritizeRecentBooks(true);
            builder.searchMode("strict");
            builder.includeOutOfStock(true); // Researchers might want all books
        }
        // Browsing intent indicators
        else if (message.contains("browse") || message.contains("explore") ||
                 message.contains("discover") || message.contains("similar") ||
                 message.contains("like")) {
            builder.userIntent("browsing");
            builder.searchMode("fuzzy");
            builder.maxResults(75); // More results for browsing
        }
        // Default to general intent
        else {
            builder.userIntent("general");
        }
    }

    /**
     * Detect search mode from language patterns
     */
    private void detectSearchMode(String userMessage, BookSearchCriteria.BookSearchCriteriaBuilder builder) {
        String message = userMessage.toLowerCase();

        if (message.contains("exactly") || message.contains("precisely") ||
            message.contains("strict") || message.matches(".*\".*\".*")) {
            builder.searchMode("strict");
        }
        else if (message.contains("similar") || message.contains("like") ||
                 message.contains("something like") || message.contains("fuzzy")) {
            builder.searchMode("fuzzy");
        }
        else {
            builder.searchMode("smart"); // Default
        }
    }

    /**
     * Detect sorting and prioritization preferences
     */
    private void detectSortingPreferences(String userMessage, BookSearchCriteria.BookSearchCriteriaBuilder builder) {
        String message = userMessage.toLowerCase();

        // Year-based sorting
        if (message.contains("latest") || message.contains("newest") || message.contains("recent")) {
            builder.sortBy("year");
            builder.sortOrder("desc");
            builder.prioritizeRecentBooks(true);
        }
        else if (message.contains("oldest") || message.contains("classic") || message.contains("vintage")) {
            builder.sortBy("year");
            builder.sortOrder("asc");
        }
        // Popularity-based sorting
        else if (message.contains("popular") || message.contains("best") ||
                 message.contains("top") || message.contains("famous")) {
            builder.sortBy("popularity");
            builder.prioritizePopularBooks(true);
        }
        // Title-based sorting
        else if (message.contains("alphabetical") || message.contains("a to z") ||
                 message.contains("z to a")) {
            builder.sortBy("title");
            builder.sortOrder(message.contains("z to a") ? "desc" : "asc");
        }
        // Author-based sorting
        else if (message.contains("by author")) {
            builder.sortBy("author");
            builder.sortOrder("asc");
        }
        else {
            builder.sortBy("relevance"); // Default
            builder.sortOrder("desc");
        }
    }

    /**
     * Detect exclusion patterns
     */
    private void detectExclusions(String userMessage, BookSearchCriteria.BookSearchCriteriaBuilder builder) {
        String message = userMessage.toLowerCase();
        List<String> excludeGenres = new ArrayList<>();
        List<String> excludeAuthors = new ArrayList<>();

        // Common exclusion patterns
        String[] exclusionPatterns = {
            "not ", "except ", "but not ", "avoid ", "exclude ", "without "
        };

        for (String pattern : exclusionPatterns) {
            if (message.contains(pattern)) {
                // Extract words following exclusion patterns
                String[] words = message.split(pattern);
                if (words.length > 1) {
                    String exclusionPart = words[1].split("[,\\.]")[0].trim();

                    // Check if it's a genre
                    if (isGenre(exclusionPart)) {
                        excludeGenres.add(exclusionPart);
                    }
                    // Check if it's an author (contains proper case or is a known author)
                    else if (exclusionPart.matches(".*[A-Z].*") || isKnownAuthor(exclusionPart)) {
                        excludeAuthors.add(exclusionPart);
                    }
                }
            }
        }

        if (!excludeGenres.isEmpty()) builder.excludeGenres(excludeGenres);
        if (!excludeAuthors.isEmpty()) builder.excludeAuthors(excludeAuthors);
    }

    /**
     * Detect language preference
     */
    private void detectLanguagePreference(String userMessage, BookSearchCriteria.BookSearchCriteriaBuilder builder) {
        boolean hasBengali = userMessage.matches(".*[\\u0980-\\u09FF].*");
        boolean hasEnglish = userMessage.matches(".*[a-zA-Z].*");

        if (hasBengali && hasEnglish) {
            builder.language("any");
        } else if (hasBengali) {
            builder.language("bengali");
        } else {
            builder.language("english");
        }
    }

    /**
     * Detect availability preferences
     */
    private void detectAvailabilityPreferences(String userMessage, BookSearchCriteria.BookSearchCriteriaBuilder builder) {
        String message = userMessage.toLowerCase();

        if (message.contains("available now") || message.contains("in stock")) {
            builder.includeOutOfStock(false);
        }
        else if (message.contains("any books") || message.contains("all books") ||
                 message.contains("including unavailable")) {
            builder.includeOutOfStock(true);
        }
        else {
            builder.includeOutOfStock(false); // Default to available only
        }
    }

    /**
     * Detect quantity preferences
     */
    private void detectQuantityPreferences(String userMessage, BookSearchCriteria.BookSearchCriteriaBuilder builder) {
        String message = userMessage.toLowerCase();

        // Extract numeric quantities
        if (message.matches(".*\\b(\\d+)\\s+(books?|results?).*")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\b(\\d+)\\s+(?:books?|results?)");
            java.util.regex.Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                int quantity = Integer.parseInt(matcher.group(1));
                builder.maxResults(Math.min(quantity, 100)); // Cap at 100
            }
        }
        // Qualitative quantities
        else if (message.contains("few") || message.contains("some")) {
            builder.maxResults(10);
        }
        else if (message.contains("many") || message.contains("lots") || message.contains("plenty")) {
            builder.maxResults(100);
        }
        else if (message.contains("at least")) {
            // Extract number after "at least"
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("at least\\s+(\\d+)");
            java.util.regex.Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                int minQuantity = Integer.parseInt(matcher.group(1));
                builder.maxResults(Math.max(minQuantity, 50));
            }
        }
        else {
            builder.maxResults(50); // Default
        }
    }

    /**
     * Helper method to check if a word is a genre
     */
    private boolean isGenre(String word) {
        String[] commonGenres = {
            "fiction", "romance", "mystery", "thriller", "fantasy", "science-fiction",
            "biography", "history", "self-help", "children", "poetry", "drama",
            "horror", "adventure", "comedy", "tragedy", "western", "crime"
        };

        for (String genre : commonGenres) {
            if (genre.equalsIgnoreCase(word) || word.contains(genre)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Helper method to check if a word/phrase is a known author
     */
    private boolean isKnownAuthor(String phrase) {
        String[] knownAuthors = {
            "shakespeare", "dickens", "austen", "tolkien", "rowling", "stephen king",
            "agatha christie", "mark twain", "hemingway", "orwell",
            "রবীন্দ্রনাথ", "নজরুল", "হুমায়ূন", "শরৎচন্দ্র"
        };

        for (String author : knownAuthors) {
            if (phrase.toLowerCase().contains(author.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extract years from the message using regex patterns
     */
    private void extractYears(String message, BookSearchCriteria.BookSearchCriteriaBuilder builder) {
        // Patterns for different year formats
        java.util.regex.Pattern[] yearPatterns = {
            java.util.regex.Pattern.compile("\\b(\\d{4})\\s*[-–—to]\\s*(\\d{4})\\b"),  // 1990-2000
            java.util.regex.Pattern.compile("between\\s+(\\d{4})\\s+and\\s+(\\d{4})"),   // between 1990 and 2000
            java.util.regex.Pattern.compile("after\\s+(\\d{4})"),                       // after 1990
            java.util.regex.Pattern.compile("before\\s+(\\d{4})"),                      // before 2000
            java.util.regex.Pattern.compile("in\\s+(\\d{4})"),                          // in 1995
            java.util.regex.Pattern.compile("\\b(\\d{4})\\b")                          // standalone year
        };

        for (java.util.regex.Pattern pattern : yearPatterns) {
            java.util.regex.Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                if (pattern.pattern().contains("[-–—to]") || pattern.pattern().contains("between")) {
                    // Range pattern
                    builder.yearFrom(Integer.parseInt(matcher.group(1)));
                    builder.yearTo(Integer.parseInt(matcher.group(2)));
                    return;
                } else if (pattern.pattern().contains("after")) {
                    builder.yearFrom(Integer.parseInt(matcher.group(1)));
                    return;
                } else if (pattern.pattern().contains("before")) {
                    builder.yearTo(Integer.parseInt(matcher.group(1)));
                    return;
                } else if (pattern.pattern().contains("in")) {
                    int year = Integer.parseInt(matcher.group(1));
                    builder.yearFrom(year);
                    builder.yearTo(year);
                    return;
                } else {
                    // Standalone year
                    int year = Integer.parseInt(matcher.group(1));
                    if (year >= 1000 && year <= 2030) {  // Valid year range
                        builder.yearFrom(year);
                        builder.yearTo(year);
                        return;
                    }
                }
            }
        }
    }

    /**
     * Extract genres from the message
     */
    private void extractGenres(String message, BookSearchCriteria.BookSearchCriteriaBuilder builder) {
        Map<String, String[]> genreKeywords = new HashMap<>();
        genreKeywords.put("fiction", new String[]{"fiction", "novel", "story"});
        genreKeywords.put("romance", new String[]{"romance", "love", "romantic"});
        genreKeywords.put("mystery", new String[]{"mystery", "detective", "crime", "thriller"});
        genreKeywords.put("science-fiction", new String[]{"science fiction", "sci-fi", "space", "future"});
        genreKeywords.put("fantasy", new String[]{"fantasy", "magic", "dragon", "wizard"});
        genreKeywords.put("biography", new String[]{"biography", "memoir", "life story"});
        genreKeywords.put("history", new String[]{"history", "historical", "past"});
        genreKeywords.put("self-help", new String[]{"self help", "improvement", "motivation", "success"});
        genreKeywords.put("children", new String[]{"children", "kids", "child"});
        genreKeywords.put("education", new String[]{"education", "learning", "study", "academic", "textbook"});
        genreKeywords.put("programming", new String[]{"programming", "coding", "software", "developer", "computer science", "algorithm", "java", "python", "javascript"});
        genreKeywords.put("technology", new String[]{"technology", "tech", "digital", "internet", "web", "mobile", "app", "development"});
        genreKeywords.put("business", new String[]{"business", "management", "entrepreneurship", "startup", "finance", "economics"});
        genreKeywords.put("science", new String[]{"science", "physics", "chemistry", "biology", "mathematics", "engineering"});
        genreKeywords.put("health", new String[]{"health", "medical", "medicine", "fitness", "nutrition", "wellness"});
        genreKeywords.put("art", new String[]{"art", "design", "photography", "painting", "creative", "drawing"});

        List<String> detectedGenres = new ArrayList<>();

        for (Map.Entry<String, String[]> entry : genreKeywords.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (message.contains(keyword)) {
                    detectedGenres.add(entry.getKey());
                    break;
                }
            }
        }

        if (!detectedGenres.isEmpty()) {
            builder.genres(detectedGenres);
        }
    }

    /**
     * Extract general keywords from the message
     */
    private List<String> extractGeneralKeywords(String message) {
        // Remove common stop words and extract meaningful keywords
        String[] stopWords = {
            "i", "am", "is", "are", "the", "a", "an", "book", "books", "looking", "for",
            "want", "need", "find", "do", "you", "have", "any", "some", "can", "could",
            "would", "should", "about", "on", "in", "at", "by", "with", "from", "to",
            "published", "written", "author", "title"
        };

        Set<String> stopWordSet = Set.of(stopWords);

        String cleanedMessage = message
            .replaceAll("[^\\p{L}\\p{N}\\s]", " ")  // Remove punctuation
            .replaceAll("\\s+", " ")                 // Normalize whitespace
            .toLowerCase()
            .trim();

        List<String> keywords = new ArrayList<>();
        String[] words = cleanedMessage.split("\\s+");

        for (String word : words) {
            if (word.length() > 2 && !stopWordSet.contains(word) && !word.matches("\\d+")) {
                keywords.add(word);
            }
        }

        return keywords;
    }

    /**
     * Detect whether user wants OR or AND logic for genre searches based on NLP analysis
     */
    private String detectGenreSearchOperation(String userMessage) {
        String message = userMessage.toLowerCase();

        // AND indicators (English)
        String[] andIndicators = {
            "both", "and", "with both", "containing all", "must have both",
            "all of", "include all", "that have all", "with all"
        };

        // OR indicators (English)
        String[] orIndicators = {
            " or ", "either", "any of", "one of", "some of"
        };

        // Bengali AND indicators
        String[] bengaliAndIndicators = {
            "এবং", "আর", "ও", "উভয়", "দুটোই", "সব", "সকল"
        };

        // Bengali OR indicators
        String[] bengaliOrIndicators = {
            "অথবা", "বা", "কিংবা", "যেকোনো", "কোনো একটি"
        };

        // Check for AND indicators first (more specific)
        for (String indicator : andIndicators) {
            if (message.contains(indicator)) {
                return "AND";
            }
        }

        for (String indicator : bengaliAndIndicators) {
            if (message.contains(indicator)) {
                return "AND";
            }
        }

        // Check for OR indicators
        for (String indicator : orIndicators) {
            if (message.contains(indicator)) {
                return "OR";
            }
        }

        for (String indicator : bengaliOrIndicators) {
            if (message.contains(indicator)) {
                return "OR";
            }
        }

        // Default to OR if no clear indicators found
        return "OR";
    }
}
