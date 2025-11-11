package com.home.knowledge.comment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class CommentRepository {
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Comment> rowMapper = (rs, rowNum) -> new Comment(
            rs.getLong("id"),
            rs.getLong("post_id"),
            rs.getString("username"),
            rs.getString("content"),
            rs.getTimestamp("created_at").toInstant()
    );

    public CommentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Comment save(long postId, String username, String content) {
        String sql = "INSERT INTO comments (post_id, username, content, created_at) VALUES (?, ?, ?, ?)";
        KeyHolder kh = new GeneratedKeyHolder();
        Instant now = Instant.now();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"ID"});
            ps.setLong(1, postId);
            ps.setString(2, username);
            ps.setString(3, content);
            ps.setTimestamp(4, Timestamp.from(now));
            return ps;
        }, kh);
        Number key = kh.getKey();
        long id = key != null ? key.longValue() : -1L;
        return new Comment(id, postId, username, content, now);
    }

    public Map<Long, List<Comment>> findByPostIds(Collection<Long> postIds) {
        Map<Long, List<Comment>> map = new HashMap<>();
        if (postIds == null || postIds.isEmpty()) return map;

        String inSql = String.join(",", Collections.nCopies(postIds.size(), "?"));
        String sql = "SELECT id, post_id, username, content, created_at FROM comments WHERE post_id IN (" + inSql + ") ORDER BY created_at ASC, id ASC";
        List<Object> args = new ArrayList<>(postIds);
        jdbcTemplate.query(sql, rowMapper, args.toArray()).forEach(c -> {
            map.computeIfAbsent(c.getPostId(), k -> new ArrayList<>()).add(c);
        });
        return map;
    }

    public List<Comment> findByPostId(long postId) {
        String sql = "SELECT id, post_id, username, content, created_at FROM comments WHERE post_id = ? ORDER BY created_at ASC, id ASC";
        return jdbcTemplate.query(sql, rowMapper, postId);
    }

    public Comment findById(long id) {
        String sql = "SELECT id, post_id, username, content, created_at FROM comments WHERE id = ?";
        return jdbcTemplate.query(sql, rowMapper, id).stream().findFirst().orElse(null);
    }

    public int updateContent(long id, String content) {
        return jdbcTemplate.update("UPDATE comments SET content = ? WHERE id = ?", content, id);
    }

    public Map<Long, Integer> countByPostIds(Collection<Long> postIds) {
        Map<Long, Integer> counts = new HashMap<>();
        if (postIds == null || postIds.isEmpty()) {
            return counts;
        }
        String inSql = String.join(",", Collections.nCopies(postIds.size(), "?"));
        String sql = "SELECT post_id, COUNT(*) AS cnt FROM comments WHERE post_id IN (" + inSql + ") GROUP BY post_id";
        List<Object> args = new ArrayList<>(postIds);
        jdbcTemplate.query(sql, args.toArray(), rs -> {
            counts.put(rs.getLong("post_id"), rs.getInt("cnt"));
        });
        return counts;
    }

    public int delete(long id) {
        return jdbcTemplate.update("DELETE FROM comments WHERE id = ?", id);
    }
}
