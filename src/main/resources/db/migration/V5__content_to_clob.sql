-- posts.content を TEXT 化（安全手順）
ALTER TABLE posts ADD COLUMN content_new TEXT;
UPDATE posts SET content_new = content;
ALTER TABLE posts DROP COLUMN content;
ALTER TABLE posts RENAME COLUMN content_new TO content;

-- comments.content を TEXT 化（安全手順）
ALTER TABLE comments ADD COLUMN content_new TEXT;
UPDATE comments SET content_new = content;
ALTER TABLE comments DROP COLUMN content;
ALTER TABLE comments RENAME COLUMN content_new TO content;
