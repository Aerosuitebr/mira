package com.prospectportal.config;



import com.prospectportal.common.repository.UserRepository;

import com.prospectportal.security.AuthenticatedUser;

import com.prospectportal.security.JwtService;

import jakarta.servlet.FilterChain;

import jakarta.servlet.ServletException;

import jakarta.servlet.http.HttpServletRequest;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;

import org.springframework.stereotype.Component;

import org.springframework.web.filter.OncePerRequestFilter;



import java.io.IOException;



@Component

public class JwtAuthenticationFilter extends OncePerRequestFilter {



    private final JwtService jwtService;

    private final UserRepository userRepository;



    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {

        this.jwtService = jwtService;

        this.userRepository = userRepository;

    }



    @Override

    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)

        throws ServletException, IOException {



        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {

            filterChain.doFilter(request, response);

            return;

        }



        String token = header.substring(7);

        if (jwtService.isValid(token)) {

            String email = jwtService.extractEmail(token);

            userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {

                AuthenticatedUser principal = new AuthenticatedUser(

                    user.getId(),

                    user.getTenant().getId(),

                    user.getEmail(),

                    user.getFullName(),

                    user.getRole()

                );

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(

                    principal, null, principal.getAuthorities()

                );

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);

            });

        }



        filterChain.doFilter(request, response);

    }

}


