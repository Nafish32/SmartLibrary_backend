package com.eksooteeksoo.smartlibraryse.Repository;

import com.eksooteeksoo.smartlibraryse.Model.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {
    List<Book> findByQuantityGreaterThan(int quantity);

    // Year range search methods
    List<Book> findByPublishedYearBetweenAndQuantityGreaterThan(int yearFrom, int yearTo, int quantity);

    @Query("SELECT b FROM Book b WHERE b.quantity > 0 AND " +
           "(LOWER(b.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(b.author) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(COALESCE(b.authorBengali, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(b.genre) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    List<Book> searchAvailableBooks(@Param("searchTerm") String searchTerm);

    // Simple native query for multiple genre search (AND logic)
    @Query(value = "SELECT * FROM books WHERE quantity > 0 AND " +
           "(:genre1 IS NULL OR LOWER(genre) LIKE LOWER(CONCAT('%', :genre1, '%'))) AND " +
           "(:genre2 IS NULL OR LOWER(genre) LIKE LOWER(CONCAT('%', :genre2, '%'))) AND " +
           "(:genre3 IS NULL OR LOWER(genre) LIKE LOWER(CONCAT('%', :genre3, '%')))",
           nativeQuery = true)
    List<Book> findBooksContainingAllGenres(@Param("genre1") String genre1,
                                           @Param("genre2") String genre2,
                                           @Param("genre3") String genre3);

    List<Book> findByTitleContainingIgnoreCaseAndQuantityGreaterThan(String title, int quantity);

    @Query("SELECT b FROM Book b WHERE b.quantity > :quantity AND " +
           "(LOWER(b.author) LIKE LOWER(CONCAT('%', :author, '%')) OR " +
           "LOWER(COALESCE(b.authorBengali, '')) LIKE LOWER(CONCAT('%', :author, '%')))")
    List<Book> findByAuthorContainingIgnoreCaseAndQuantityGreaterThan(@Param("author") String author, @Param("quantity") int quantity);

    List<Book> findByGenreContainingIgnoreCaseAndQuantityGreaterThan(String genre, int quantity);
    List<Book> findByPublishedYearAndQuantityGreaterThan(int publishedYear, int quantity);
    List<Book> findByDescriptionContainingIgnoreCaseAndQuantityGreaterThan(String description, int quantity);
    List<Book> findByTitleContainingIgnoreCaseAndDescriptionContainingIgnoreCaseAndQuantityGreaterThan(
        String title, String description, int quantity);

    // Advanced search method with multiple filters including bilingual author support
    @Query("SELECT DISTINCT b FROM Book b WHERE b.quantity > 0 AND " +
           "(:title IS NULL OR LOWER(b.title) LIKE LOWER(CONCAT('%', :title, '%'))) AND " +
           "(:author IS NULL OR LOWER(b.author) LIKE LOWER(CONCAT('%', :author, '%')) OR " +
           "LOWER(COALESCE(b.authorBengali, '')) LIKE LOWER(CONCAT('%', :author, '%'))) AND " +
           "(:genre IS NULL OR LOWER(b.genre) LIKE LOWER(CONCAT('%', :genre, '%'))) AND " +
           "(:yearFrom IS NULL OR b.publishedYear >= :yearFrom) AND " +
           "(:yearTo IS NULL OR b.publishedYear <= :yearTo) AND " +
           "(:descriptionKeyword IS NULL OR LOWER(b.description) LIKE LOWER(CONCAT('%', :descriptionKeyword, '%'))) AND " +
           "(:isbn IS NULL OR b.isbn = :isbn)")
    List<Book> searchBooksWithFilters(
        @Param("title") String title,
        @Param("author") String author,
        @Param("genre") String genre,
        @Param("yearFrom") Integer yearFrom,
        @Param("yearTo") Integer yearTo,
        @Param("descriptionKeyword") String descriptionKeyword,
        @Param("isbn") String isbn
    );
}
