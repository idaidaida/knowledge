package com.home.knowledge.like;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
public class LikeRepository {
    private final JdbcTemplate jdbcTemplate;

    public LikeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int countByPostId(long postId) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM likes WHERE post_id = ?", Integer.class, postId);
        return c != null ? c : 0;
    }

    public boolean likedByUser(long postId, String username) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM likes WHERE post_id = ? AND username = ?", Integer.class, postId, username);
        return c != null && c > 0;
    }

    public void like(long postId, String username) {
        jdbcTemplate.update(
                "INSERT INTO likes (post_id, username) VALUES (?, ?) ON CONFLICT (post_id, username) DO NOTHING",
                postId, username
        );
    }

    public void unlike(long postId, String username) {
        jdbcTemplate.update("DELETE FROM likes WHERE post_id = ? AND username = ?", postId, username);
    }

    public Set<Long> findPostIdsByUser(String username) {
        if (!StringUtils.hasText(username)) {
            return Set.of();
        }
        String sql = "SELECT post_id FROM likes WHERE username = ?";
        List<Long> ids = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("post_id"), username);
        return new HashSet<>(ids);
    }
}
