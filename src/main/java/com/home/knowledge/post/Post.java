package com.home.knowledge.post;

import java.time.Instant;

public class Post {
    private final long id;
    private final String username;
    private final String title;
    private final String content;
    private final String imageUrl;
    private final String linkUrl;
    private final String summary;
    private final Instant createdAt;

    public Post(long id, String username, String title, String content, String imageUrl, String linkUrl, String summary, Instant createdAt) {
        this.id = id;
        this.username = username;
        this.title = title;
        this.content = content;
        this.imageUrl = imageUrl;
        this.linkUrl = linkUrl;
        this.summary = summary;
        this.createdAt = createdAt;
    }

    public long getId() { return id; }
    public String getUsername() { return username; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getImageUrl() { return imageUrl; }
    public String getLinkUrl() { return linkUrl; }
    public String getSummary() { return summary; }
    public Instant getCreatedAt() { return createdAt; }
}
