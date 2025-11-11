-- V11__add_summary_text.sql
ALTER TABLE posts ADD COLUMN IF NOT EXISTS summary TEXT;
