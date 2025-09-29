package com.eksooteeksoo.smartlibraryse.Model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Data
@Table(name = "users")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Usr {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String userName;

    @Column(nullable = false)
    private String password;

    private String email;
    private String fullName;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**tells jpa to store this in a different table and link to the user table*/
    @ElementCollection(targetClass = Role.class , fetch = FetchType.EAGER/**tells the jpa to load roles when user loads as well*/)
    /**
     * @CollectionTable(...): Defines the join table that will store the user-to-role mappings.
     * - name = "user_roles": The name of the table.
     * - joinColumns = @JoinColumn(name = "user_id"): The foreign key column in "user_roles" that
     * references the 'id' of the 'users' table.
     */
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)//makes the enum string in the db
    @Column(name = "role")//name of the column in the user_roles table
    private Set<Role> roles;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
