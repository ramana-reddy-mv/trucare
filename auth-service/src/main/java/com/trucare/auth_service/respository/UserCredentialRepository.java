package com.trucare.auth_service.respository;

import com.trucare.auth_service.entity.UserCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserCredentialRepository extends JpaRepository<UserCredential, Integer> {
    boolean existsByNameIgnoreCase(String name);
    boolean existsByEmailIgnoreCase(String email);
    Optional<UserCredential> findByNameIgnoreCaseOrEmailIgnoreCase(String name, String email);
}
