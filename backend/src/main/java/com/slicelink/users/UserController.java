package com.slicelink.users;

import com.slicelink.auth.AuthenticatedUser;
import com.slicelink.shared.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated user", security = @SecurityRequirement(name = "bearerAuth"))
    public UserResponse currentUser(@AuthenticationPrincipal AuthenticatedUser principal) {
        User user = userRepository.findById(principal.id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required."));
        return UserResponse.from(user);
    }
}
