package com.webchat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import com.webchat.service.AdminAccountService;
import com.webchat.service.UserService;
import com.webchat.model.User;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:webchat-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "webchat.data-dir=./.runtime-data/db-test/"
})
class DatabaseIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired AdminAccountService adminAccounts;
    @Autowired UserService users;

    @Test
    void schemaAndDatabaseRepositoriesStart() {
        assertNotNull(jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class));
        assertNotNull(jdbc.queryForObject("SELECT COUNT(*) FROM matches", Long.class));
        assertNotNull(jdbc.queryForObject("SELECT COUNT(*) FROM admin_logs", Long.class));
        assertNotNull(jdbc.queryForObject("SELECT COUNT(*) FROM admin_accounts", Long.class));
    }

    @Test
    void existingUserCanRegisterAndLoginAsAdminWithInviteCode() {
        User user = new User();
        user.setUsername("admin_candidate"); user.setPassword("secret123");
        users.register(user);
        org.junit.jupiter.api.Assertions.assertNull(
                adminAccounts.register("admin_candidate", "secret123", "13412341", "13412341"));
        org.junit.jupiter.api.Assertions.assertNotNull(adminAccounts.login("admin_candidate", "secret123"));
    }
}
