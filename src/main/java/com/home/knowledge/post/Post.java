package com.home.knowledge.post;

import java.time.Instant;

public class Post {
    private final long id;
    private final String username;
    private final String title;
    private final String content;
    private final String imageUrl;
    private final String linkUrl;
    private final Instant createdAt;

    public Post(long id, String username, String title, String content, String imageUrl, String linkUrl, Instant createdAt) {
        this.id = id;
        this.username = username;
        this.title = title;
        this.content = content;
        this.imageUrl = imageUrl;
        this.linkUrl = linkUrl;
        this.createdAt = createdAt;
    }

    public long getId() { return id; }
    public String getUsername() { return username; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getImageUrl() { return imageUrl; }
    public String getLinkUrl() { return linkUrl; }
    public Instant getCreatedAt() { return createdAt; }
}
