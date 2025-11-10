CREATE TABLE IF NOT EXISTS posts (
    id IDENTITY PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    content VARCHAR(280) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_posts_username_created_at ON posts(username, created_at DESC);

