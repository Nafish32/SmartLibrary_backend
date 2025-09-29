package com.eksooteeksoo.smartlibraryse.DTO;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserRegistrationDTO {
    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;

    private String email;
    private String fullName;

    @NotBlank(message = "Role is required")
    private String role; // "USER" or "ADMIN"

    private String adminKey; // Required only if role is "ADMIN"
}
