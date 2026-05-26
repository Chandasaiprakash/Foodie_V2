package com.foodie.user_service.controller;

// User Service: com.foodie.user_service.controller.InternalUserController


import com.foodie.user_service.dto.UserAuthDetails;
import com.foodie.user_service.model.User;
import com.foodie.user_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserRepository userRepository;

    @GetMapping("/by-email/{email}")
    public UserAuthDetails getUserByEmailForAuth(@PathVariable String email,
                                                 @RequestHeader(name = "X-Internal-Secret", required = false) String secret) {

        if (!"YOUR_INTERNAL_SHARED_SECRET".equals(secret)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied: Internal Secret missing or invalid");
        }

        User u = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        return new UserAuthDetails(
                u.getId(),
                u.getEmail(),
                u.getPassword(),
                u.getRole().name(), // ✅ enum to String conversion
                u.getUsername(),
                u.getPhoneNumber()
        );
    }
}

