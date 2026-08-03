package com.prospectportal.module.auth;

import com.prospectportal.common.entity.User;
import com.prospectportal.common.repository.UserRepository;
import com.prospectportal.security.JwtService;
import com.prospectportal.web.dto.AuthResponse;
import com.prospectportal.web.dto.LoginRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email())
            .filter(User::isActive)
            .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Credenciais inválidas"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(UNAUTHORIZED, "Credenciais inválidas");
        }

        String token = jwtService.generateToken(user.getEmail());
        var tenant = user.getTenant();
        int creditsRemaining = tenant.getMonthlyCredits() - tenant.getCreditsUsed();

        return new AuthResponse(
            token,
            user.getId(),
            tenant.getId(),
            user.getFullName(),
            user.getEmail(),
            tenant.getPlanCode(),
            creditsRemaining,
            tenant.getMonthlyCredits()
        );
    }
}
