package com.home.knowledge.like;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
        jdbcTemplate.update("MERGE INTO likes (post_id, username) KEY(post_id, username) VALUES (?, ?)", postId, username);
    }

    public void unlike(long postId, String username) {
        jdbcTemplate.update("DELETE FROM likes WHERE post_id = ? AND username = ?", postId, username);
    }
}

