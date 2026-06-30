package com.neusoft.cloudbrain.auth.config;

import com.neusoft.cloudbrain.auth.entity.Role;
import com.neusoft.cloudbrain.auth.entity.UserAccount;
import com.neusoft.cloudbrain.auth.repository.RoleRepository;
import com.neusoft.cloudbrain.auth.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Initializes the first admin account when it does not exist.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements CommandLineRunner {

    private final UserAccountRepository userAccountRepository;
    private final RoleRepository roleRepository;
    private final AdminInitProperties adminInitProperties;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);

    @Override
    @Transactional
    public void run(String... args) {
        if (!userAccountRepository.existsByUsername(adminInitProperties.getUsername())) {
            log.info("Creating initial admin account: {}", adminInitProperties.getUsername());

            LocalDateTime now = LocalDateTime.now();
            Role adminRole = roleRepository.findByName("ADMIN")
                    .orElseGet(() -> roleRepository.save(Role.builder()
                            .name("ADMIN")
                            .description("System administrator")
                            .createdAt(now)
                            .updatedAt(now)
                            .build()));

            UserAccount adminUser = UserAccount.builder()
                    .username(adminInitProperties.getUsername())
                    .passwordHash(passwordEncoder.encode(adminInitProperties.getPassword()))
                    .enabled(true)
                    .mustChangePassword(true)
                    .createdAt(now)
                    .updatedAt(now)
                    .roles(Set.of(adminRole))
                    .build();

            userAccountRepository.save(adminUser);

            log.info("Initial admin account created; password change is required on first login");
        } else {
            log.info("Admin account already exists; skipping initialization");
        }
    }
}
