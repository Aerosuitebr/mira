package com.prospectportal.config;

import com.prospectportal.common.entity.User;
import com.prospectportal.common.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("dev")
public class DevUserBootstrap {

    @Bean
    CommandLineRunner ensureDemoPassword(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> userRepository.findByEmailIgnoreCase("demo@prospectportal.com").ifPresent(user -> {
            user.setPasswordHash(passwordEncoder.encode("demo123"));
            userRepository.save(user);
        });
    }
}
