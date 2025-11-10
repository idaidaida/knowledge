CREATE TABLE IF NOT EXISTS app_users (
    id VARCHAR(50) PRIMARY KEY,
    password VARCHAR(255) NOT NULL
);

MERGE INTO app_users (id, password) KEY(id) VALUES
    ('yuhei', 'yuhei'),
    ('shiho', 'shiho');

