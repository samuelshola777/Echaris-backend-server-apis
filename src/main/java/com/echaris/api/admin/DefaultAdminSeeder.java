package com.echaris.api.admin;

import com.echaris.api.userauthmgt.user.User;
import com.echaris.api.userauthmgt.user.UserService;
import com.echaris.api.utils.Gender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultAdminSeeder {

    private final AdminRepository adminRepository;
    private final UserService userService;

    @Value("${ADMIN_BOOTSTRAP_EMAIL:echarispersonal@gmail.com}") private String email;
    @Value("${ADMIN_BOOTSTRAP_PASSWORD:password}") private String password;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedDefaultSuperAdmin() {

        if (adminRepository.countByDeleted(false) > 0) {
            log.info("Admin already exists. Skipping default admin creation");
            return;
        }

        if (email == null || email.isBlank()) {
            throw new IllegalStateException(
                    "ADMIN_BOOTSTRAP_EMAIL is required to create the first admin"
            );
        }

        if (password == null
                || password.isBlank()
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException(
                    "Bootstrap password is required "
                            + "and must not exceed 72 UTF-8 bytes"
            );
        }

        if (userService.existsByEmailAddressIgnoreCase(email.trim())) {
            throw new IllegalStateException(
                    "Bootstrap email already belongs to a user. "
                            + "Review that account before creating an admin"
            );
        }

        User user = userService.createAdminUserFromAdminModule(
                "Road ResQ",
                "Administrator",
                email.trim().toLowerCase(Locale.ROOT),
                password,
                null,
                null,
                "#0862FF",
                Gender.MALE,
                true
        );

        Admin admin = Admin.builder()
                .userId(user.getId())
                .active(true)
                .deleted(false)
                .build();

        admin.setSuperAdmin(true);

        adminRepository.save(admin);

        log.info("Default super admin created successfully");
    }
}