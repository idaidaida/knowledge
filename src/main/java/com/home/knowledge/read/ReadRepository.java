package com.home.knowledge.read;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
public class ReadRepository {
    private final JdbcTemplate jdbcTemplate;

    public ReadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void markRead(long postId, String username) {
        jdbcTemplate.update("MERGE INTO reads (post_id, username) KEY(post_id, username) VALUES (?, ?)", postId, username);
    }

    public Set<Long> findReadPostIds(String username) {
        List<Long> list = jdbcTemplate.query("SELECT post_id FROM reads WHERE username = ?", (rs, i) -> rs.getLong(1), username);
        return new HashSet<>(list);
    }

    public List<String> findReadersByPostId(long postId) {
        String sql = "SELECT username FROM reads WHERE post_id = ? ORDER BY username";
        return jdbcTemplate.query(sql, (rs, i) -> rs.getString(1), postId);
    }
}
