CREATE TABLE IF NOT EXISTS user_seen_items (
    username VARCHAR(50) NOT NULL,
    kind VARCHAR(16) NOT NULL, -- 'POST' or 'COMMENT'
    ref_id BIGINT NOT NULL,
    seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_user_seen_items PRIMARY KEY (username, kind, ref_id)
);

