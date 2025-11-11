package com.home.knowledge.post;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class PostRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Post> rowMapper = (rs, rowNum) -> new Post(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("title"),
            rs.getString("content"),
            rs.getString("image_url"),
            rs.getString("link_url"),
            rs.getString("summary"),
            rs.getTimestamp("created_at").toInstant()
    );

    public PostRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Post save(String username, String title, String content, String imageUrl, String linkUrl, String summary) {
        String sql = "INSERT INTO posts (username, title, content, image_url, link_url, summary, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        Instant now = Instant.now();

        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"ID"});
            ps.setString(1, username);
            ps.setString(2, title);
            ps.setString(3, content);
            ps.setString(4, imageUrl);
            ps.setString(5, linkUrl);
            ps.setString(6, summary);
            ps.setTimestamp(7, Timestamp.from(now));
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        long id = key != null ? key.longValue() : -1L;
        return new Post(id, username, title, content, imageUrl, linkUrl, summary, now);
    }

    public List<Post> findAll() {
        String sql = "SELECT id, username, title, content, image_url, link_url, summary, created_at FROM posts ORDER BY created_at DESC, id DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public List<Post> findByUsername(String username) {
        String sql = "SELECT id, username, title, content, image_url, link_url, summary, created_at FROM posts WHERE username = ? ORDER BY created_at DESC, id DESC";
        return jdbcTemplate.query(sql, rowMapper, username);
    }

    public Optional<Post> findById(long id) {
        try {
            String sql = "SELECT id, username, title, content, image_url, link_url, summary, created_at FROM posts WHERE id = ?";
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public int update(long id, String title, String content, String imageUrl, String linkUrl) {
        String sql = "UPDATE posts SET title = ?, content = ?, image_url = ?, link_url = ? WHERE id = ?";
        return jdbcTemplate.update(sql, title, content, imageUrl, linkUrl, id);
    }

    public int delete(long id) {
        return jdbcTemplate.update("DELETE FROM posts WHERE id = ?", id);
    }
}
