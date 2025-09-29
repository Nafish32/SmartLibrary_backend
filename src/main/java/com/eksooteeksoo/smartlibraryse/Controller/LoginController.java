package com.eksooteeksoo.smartlibraryse.Controller;

import com.eksooteeksoo.smartlibraryse.DTO.LoginRequest;
import com.eksooteeksoo.smartlibraryse.DTO.LoginResponse;
import com.eksooteeksoo.smartlibraryse.DTO.UserRegistrationDTO;
import com.eksooteeksoo.smartlibraryse.Security.JwtUtils;
import com.eksooteeksoo.smartlibraryse.Service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*", maxAge = 3600)
public class LoginController {

    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final UserService userService;

    public LoginController(AuthenticationManager authenticationManager,
                          JwtUtils jwtUtils,
                          UserService userService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            logger.info("Login attempt for user: {}", loginRequest.getUsername());

            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword())
            );

            String jwt = jwtUtils.generateJwtToken(authentication);

            String role = authentication.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElse("ROLE_USER");

            logger.info("User {} logged in successfully with role {}", loginRequest.getUsername(), role);
            return ResponseEntity.ok(new LoginResponse(jwt, loginRequest.getUsername(), role));

        } catch (BadCredentialsException e) {
            logger.warn("Failed login attempt for user: {}", loginRequest.getUsername());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("Invalid username or password");
        } catch (Exception e) {
            logger.error("Login error for user: {}", loginRequest.getUsername(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("An error occurred during login");
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody UserRegistrationDTO signUpRequest) {
        try {
            logger.info("Registration attempt for user: {}", signUpRequest.getUsername());

            userService.createUser(signUpRequest);

            logger.info("User {} registered successfully", signUpRequest.getUsername());
            return ResponseEntity.ok("User registered successfully!");

        } catch (IllegalArgumentException e) {
            logger.warn("Registration failed for user {}: {}", signUpRequest.getUsername(), e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            logger.error("Registration error for user: {}", signUpRequest.getUsername(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("An error occurred during registration");
        }
    }
}
