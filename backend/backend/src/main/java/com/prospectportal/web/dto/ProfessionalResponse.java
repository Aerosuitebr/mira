package com.prospectportal.web.dto;

import java.util.UUID;

public record ProfessionalResponse(
    UUID id,
    String name,
    String occupation,
    String specialties,
    String bio,
    String email,
    String whatsapp,
    String phone,
    boolean emailAvailable,
    boolean whatsappAvailable,
    boolean phoneAvailable,
    String website,
    String instagram,
    String profileImageUrl,
    Double rating,
    int reviewCount,
    Integer yearsExperience,
    boolean verified,
    String serviceMode,
    String neighborhood,
    String city,
    String state,
    double latitude,
    double longitude,
    double distanceKm,
    String source
) {}
