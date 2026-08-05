package com.prospectportal.config;



import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.context.annotation.Bean;

import org.springframework.context.annotation.Configuration;

import org.springframework.http.HttpMethod;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;

import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.web.SecurityFilterChain;

import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.web.cors.CorsConfiguration;

import org.springframework.web.cors.CorsConfigurationSource;

import org.springframework.web.cors.UrlBasedCorsConfigurationSource;



import java.util.List;



@Configuration

@EnableWebSecurity

public class SecurityConfig {



    private final JwtAuthenticationFilter jwtAuthenticationFilter;



    @Value("${app.cors.allowed-origins}")

    private String allowedOrigins;



    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {

        this.jwtAuthenticationFilter = jwtAuthenticationFilter;

    }



    @Bean

    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http

            .csrf(AbstractHttpConfigurer::disable)

            .httpBasic(AbstractHttpConfigurer::disable)

            .formLogin(AbstractHttpConfigurer::disable)

            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .exceptionHandling(ex -> ex

                .authenticationEntryPoint((request, response, authException) ->

                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED))

                .accessDeniedHandler((request, response, accessDeniedException) ->

                    response.sendError(HttpServletResponse.SC_FORBIDDEN))

            )

            .authorizeHttpRequests(auth -> auth

                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                .requestMatchers(

                    "/api/auth/**",

                    "/api/public/**",

                    "/api/webhooks/evolution",

                    "/api/webhooks/evolution/**",

                    "/api/discovery/cnaes/**",

                    "/actuator/health",

                    "/actuator/info",

                    "/error"

                ).permitAll()

                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                .anyRequest().authenticated()

            )

            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();

    }



    @Bean

    PasswordEncoder passwordEncoder() {

        return new BCryptPasswordEncoder();

    }



    @Bean

    CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOriginPatterns(List.of("*"));

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        config.setAllowedHeaders(List.of("*"));

        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", config);

        return source;

    }

}


