CREATE TABLE IF NOT EXISTS comments (
    id IDENTITY PRIMARY KEY,
    post_id BIGINT NOT NULL,
    username VARCHAR(50) NOT NULL,
    content VARCHAR(280) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_comments_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_comments_post_created_at ON comments(post_id, created_at ASC);

