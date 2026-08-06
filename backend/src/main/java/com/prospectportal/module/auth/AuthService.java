package com.prospectportal.module.auth;

import com.prospectportal.common.entity.User;
import com.prospectportal.common.entity.Tenant;
import com.prospectportal.common.repository.UserRepository;
import com.prospectportal.common.repository.TenantRepository;
import com.prospectportal.security.JwtService;
import com.prospectportal.web.dto.AuthResponse;
import com.prospectportal.web.dto.LoginRequest;
import com.prospectportal.web.dto.PublicRegisterRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TenantRepository tenantRepository;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       TenantRepository tenantRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tenantRepository = tenantRepository;
    }

    public AuthResponse login(LoginRequest request) {
        return authenticate(request, false);
    }

    public AuthResponse publicLogin(LoginRequest request) {
        return authenticate(request, true);
    }

    @Transactional
    public AuthResponse registerPublic(PublicRegisterRequest request) {
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new ResponseStatusException(CONFLICT, "E-mail já cadastrado");
        }

        Tenant tenant = new Tenant();
        tenant.setName(request.fullName().trim());
        tenant.setPlanCode("PUBLIC");
        tenant.setMonthlyCredits(1);
        tenant.setCreditsUsed(0);
        tenant = tenantRepository.save(tenant);

        User user = new User();
        user.setTenant(tenant);
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole("PUBLIC_USER");
        user.setActive(true);
        userRepository.save(user);
        return responseFor(user);
    }

    private AuthResponse authenticate(LoginRequest request, boolean publicOnly) {
        User user = userRepository.findByEmailIgnoreCase(request.email())
            .filter(User::isActive)
            .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Credenciais inválidas"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(UNAUTHORIZED, "Credenciais inválidas");
        }

        if (publicOnly && !"PUBLIC_USER".equals(user.getRole())) {
            throw new ResponseStatusException(UNAUTHORIZED, "Use o acesso administrativo para esta conta");
        }

        return responseFor(user);
    }

    private AuthResponse responseFor(User user) {
        String token = jwtService.generateToken(user.getEmail());
        var tenant = user.getTenant();
        int creditsRemaining = tenant.getMonthlyCredits() - tenant.getCreditsUsed();

        return new AuthResponse(
            token,
            user.getId(),
            tenant.getId(),
            user.getFullName(),
            user.getEmail(),
            user.getRole(),
            tenant.getPlanCode(),
            creditsRemaining,
            tenant.getMonthlyCredits()
        );
    }
}
