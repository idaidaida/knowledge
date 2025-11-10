package com.home.knowledge.comment;

import java.time.Instant;

public class Comment {
    private final long id;
    private final long postId;
    private final String username;
    private final String content;
    private final Instant createdAt;

    public Comment(long id, long postId, String username, String content, Instant createdAt) {
        this.id = id;
        this.postId = postId;
        this.username = username;
        this.content = content;
        this.createdAt = createdAt;
    }

    public long getId() { return id; }
    public long getPostId() { return postId; }
    public String getUsername() { return username; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}

