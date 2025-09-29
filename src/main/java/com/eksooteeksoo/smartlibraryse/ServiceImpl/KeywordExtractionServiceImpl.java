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
            Extract book search criteria from the user message. Respond ONLY with a JSON object containing these fields:
            {
                "titles": [exact book titles or series names mentioned - be very specific],
                "authors": [exact author names mentioned],
                "genres": [specific genres like fiction, romance, mystery, etc.],
                "keywords": [other relevant keywords for general search],
                "yearFrom": number or null (earliest publication year),
                "yearTo": number or null (latest publication year),
                "isbn": "string or null",
                "descriptionKeywords": [specific keywords that should be searched in book descriptions]
            }

            IMPORTANT RULES:
            1. For book series (like "Harry Potter", "Lord of the Rings"), put the series name in titles array
            2. If user asks about "third book" or "second book" etc., put words like "third", "second" in descriptionKeywords
            3. Be very specific with titles - don't add generic terms to titles array
            4. Only put confirmed author names in authors array
            5. For questions about availability of specific books, focus on title and description keywords
            6. Extract years from phrases like "published in 1990", "between 1980-2000", "after 1995", "before 2010"
            7. Common genres: fiction, non-fiction, romance, mystery, thriller, science-fiction, fantasy, biography, history, self-help, education, children, poetry, drama, programming, technology, business, science, health, art

            Examples:
            - "harry potter books" → titles: ["Harry Potter"], authors: [], genres: [], keywords: []
            - "third harry potter book" → titles: ["Harry Potter"], descriptionKeywords: ["third"]
            - "books by J.K. Rowling" → authors: ["J.K. Rowling"], titles: [], genres: [], keywords: []
            - "fantasy books after 2000" → genres: ["fantasy"], yearFrom: 2000
            - "books about programming" → genres: ["programming"], keywords: ["programming"]
            - "Is the third book of harry potter available?" → titles: ["Harry Potter"], descriptionKeywords: ["third"]

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

    private BookSearchCriteria extractWithRules(String userMessage) {
        String cleanMessage = userMessage.toLowerCase().trim();

        BookSearchCriteria.BookSearchCriteriaBuilder builder = BookSearchCriteria.builder();

        // Extract years using regex
        extractYears(cleanMessage, builder);

        // Extract common genres
        extractGenres(cleanMessage, builder);

        // Extract general keywords
        List<String> keywords = extractGeneralKeywords(cleanMessage);
        builder.keywords(keywords);

        // Use same keywords for description search
        builder.descriptionKeywords(new ArrayList<>(keywords));

        return builder.build();
    }

    private void extractYears(String message, BookSearchCriteria.BookSearchCriteriaBuilder builder) {
        // Patterns for different year formats
        Pattern[] yearPatterns = {
            Pattern.compile("\\b(\\d{4})\\s*[-–—to]\\s*(\\d{4})\\b"),  // 1990-2000
            Pattern.compile("between\\s+(\\d{4})\\s+and\\s+(\\d{4})"),   // between 1990 and 2000
            Pattern.compile("after\\s+(\\d{4})"),                       // after 1990
            Pattern.compile("before\\s+(\\d{4})"),                      // before 2000
            Pattern.compile("in\\s+(\\d{4})"),                          // in 1995
            Pattern.compile("\\b(\\d{4})\\b")                          // standalone year
        };

        for (Pattern pattern : yearPatterns) {
            Matcher matcher = pattern.matcher(message);
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
}
