package com.prospectportal.common.repository;

import com.prospectportal.common.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    @Query("""
        SELECT u FROM User u
        JOIN FETCH u.tenant
        WHERE LOWER(u.email) = LOWER(:email)
        """)
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);
}
