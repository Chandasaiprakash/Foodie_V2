package com.foodie.user_service;

import com.foodie.user_service.model.User;
import com.foodie.user_service.repository.UserRepository;
import com.foodie.user_service.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = new User();
        sampleUser.setId(1L);
        sampleUser.setEmail("user@example.com");
        sampleUser.setUsername("testuser");
        sampleUser.setPassword("raw-password");
        sampleUser.setRole(User.Role.CUSTOMER);
    }

    @Test
    void getAllUsers_returnsAllUsers() {
        when(userRepository.findAll()).thenReturn(List.of(sampleUser));

        List<User> users = userService.getAllUsers();

        assertThat(users).hasSize(1);
        assertThat(users.get(0).getEmail()).isEqualTo("user@example.com");
    }

    @Test
    void getUserById_returnsUser_whenFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

        User result = userService.getUserById(1L);

        assertThat(result.getEmail()).isEqualTo("user@example.com");
    }

    @Test
    void getUserById_throws404_whenNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void getUserByEmail_returnsUser_whenFound() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(sampleUser));

        Optional<User> result = userService.getUserByEmail("user@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("testuser");
    }

    @Test
    void getUserByEmail_returnsEmpty_whenNotFound() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        Optional<User> result = userService.getUserByEmail("ghost@example.com");

        assertThat(result).isEmpty();
    }

    @Test
    void saveUser_hashesPasswordAndSetsDefaultRole() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode("raw-password")).thenReturn("hashed-password");
        sampleUser.setRole(null); // simulate no role set
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = userService.saveUser(sampleUser);

        assertThat(saved.getPassword()).isEqualTo("hashed-password");
        assertThat(saved.getRole()).isEqualTo(User.Role.CUSTOMER);
    }

    @Test
    void saveUser_throws409_whenEmailAlreadyRegistered() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(sampleUser));

        assertThatThrownBy(() -> userService.saveUser(sampleUser))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Email is already registered");
    }

    @Test
    void saveUser_preservesExplicitRole_whenProvided() {
        sampleUser.setRole(User.Role.ADMIN);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User saved = userService.saveUser(sampleUser);

        assertThat(saved.getRole()).isEqualTo(User.Role.ADMIN);
    }

    @Test
    void deleteUser_deletesSuccessfully_whenFound() {
        when(userRepository.existsById(1L)).thenReturn(true);

        userService.deleteUser(1L);

        verify(userRepository).deleteById(1L);
    }

    @Test
    void deleteUser_throws404_whenNotFound() {
        when(userRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> userService.deleteUser(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void getUserByUsername_returnsUser_whenFound() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(sampleUser));

        Optional<User> result = userService.getUserByUsername("testuser");

        assertThat(result).isPresent();
    }
}
