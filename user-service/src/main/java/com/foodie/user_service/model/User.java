package com.foodie.user_service.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import org.springframework.format.annotation.NumberFormat;


import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String username;

    @Email
    @Column(unique = true, nullable = false)
    private String email;

    @NotBlank
    @Column(nullable = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;
    // CUSTOMER, ADMIN, DELIVERY

    @Pattern(regexp = "^[+]?[0-9]{7,15}$", message = "Invalid phone number")
    @Column(nullable = false)
    private String phoneNumber;

    @CollectionTable(name = "user_addresses", joinColumns = @JoinColumn(name = "user_id"))
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Address> addresses;

    public enum Role {
        CUSTOMER, ADMIN, DELIVERY
    }
}

