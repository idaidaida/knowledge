package com.home.knowledge.notify;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Repository
public class NotificationRepository {
    private final JdbcTemplate jdbcTemplate;

    public NotificationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Timestamp getLastSeen(String username) {
        List<Timestamp> list = jdbcTemplate.query("SELECT last_seen FROM user_last_seen WHERE username = ?",
                (rs, i) -> rs.getTimestamp(1), username);
        if (list.isEmpty()) {
            // initialize at current time so the first login does not show historical items
            Timestamp now = Timestamp.from(Instant.now());
            upsertLastSeen(username, now);
            return now;
        }
        return list.get(0);
    }

    public void upsertLastSeen(String username, Timestamp ts) {
        jdbcTemplate.update("MERGE INTO user_last_seen (username, last_seen) KEY(username) VALUES (?, ?)", username, ts);
    }

    public int countUnread(String username) {
        Timestamp ts = getLastSeen(username);
        String sql = "SELECT COUNT(*) FROM (" +
                " SELECT 'POST' AS kind, id AS ref_id FROM posts WHERE created_at > ?" +
                " UNION ALL " +
                " SELECT 'COMMENT' AS kind, id AS ref_id FROM comments WHERE created_at > ?" +
                ") t LEFT JOIN user_seen_items s ON s.username = ? AND s.kind = t.kind AND s.ref_id = t.ref_id " +
                "WHERE s.username IS NULL";
        Integer c = jdbcTemplate.queryForObject(sql, Integer.class, ts, ts, username);
        return c == null ? 0 : c;
    }

    public List<NotificationRow> listUnread(String username, int limit) {
        Timestamp ts = getLastSeen(username);
        String sql = "SELECT * FROM (" +
                " SELECT 'POST' AS kind, id AS ref_id, title AS title, content AS body, created_at AS created_at, username AS actor, NULL AS post_id FROM posts WHERE created_at > ?" +
                " UNION ALL " +
                " SELECT 'COMMENT' AS kind, id AS ref_id, NULL AS title, content AS body, created_at AS created_at, username AS actor, post_id FROM comments WHERE created_at > ?" +
                ") t LEFT JOIN user_seen_items s ON s.username = ? AND s.kind = t.kind AND s.ref_id = t.ref_id " +
                "WHERE s.username IS NULL ORDER BY t.created_at DESC LIMIT ?";
        return jdbcTemplate.query(sql, (rs, i) -> new NotificationRow(
                rs.getString("kind"),
                rs.getLong("ref_id"),
                rs.getString("title"),
                rs.getString("body"),
                rs.getTimestamp("created_at"),
                rs.getString("actor"),
                (Long) (rs.getObject("post_id") == null ? null : rs.getLong("post_id"))
        ), ts, ts, username, limit);
    }

    public void markSeen(String username, String kind, long refId) {
        jdbcTemplate.update("MERGE INTO user_seen_items (username, kind, ref_id) KEY(username, kind, ref_id) VALUES (?, ?, ?)", username, kind, refId);
    }

    public void markCommentsSeen(String username, Set<Long> commentIds) {
        if (commentIds == null || commentIds.isEmpty()) return;
        for (Long id : commentIds) {
            jdbcTemplate.update("MERGE INTO user_seen_items (username, kind, ref_id) KEY(username, kind, ref_id) VALUES (?, 'COMMENT', ?)", username, id);
        }
    }

    public static class NotificationRow {
        public final String kind; // POST or COMMENT
        public final long refId;
        public final String title;
        public final String body;
        public final Timestamp createdAt;
        public final String actor;
        public final Long postId; // for comments

        public NotificationRow(String kind, long refId, String title, String body, Timestamp createdAt, String actor, Long postId) {
            this.kind = kind; this.refId = refId; this.title = title; this.body = body; this.createdAt = createdAt; this.actor = actor; this.postId = postId;
        }
    }
}
