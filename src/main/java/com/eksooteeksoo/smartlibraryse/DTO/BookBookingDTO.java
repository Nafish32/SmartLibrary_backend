package com.eksooteeksoo.smartlibraryse.DTO;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BookBookingDTO {
    @NotNull(message = "Book ID is required")
    private Long bookId;

    private String notes;
}
