package com.home.knowledge.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean validate(String id, String password) {
        String sql = "SELECT COUNT(*) FROM app_users WHERE id = ? AND password = ?";
        Integer c = jdbcTemplate.queryForObject(sql, Integer.class, id, password);
        return c != null && c > 0;
    }
}

