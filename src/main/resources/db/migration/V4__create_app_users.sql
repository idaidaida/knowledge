CREATE TABLE IF NOT EXISTS app_users (
    id VARCHAR(50) PRIMARY KEY,
    password VARCHAR(255) NOT NULL
);

/* なければ挿入（H2/PG 両対応）*/
INSERT INTO app_users (id, password)
SELECT 'yuhei', 'yuhei'
WHERE NOT EXISTS (SELECT 1 FROM app_users WHERE id = 'yuhei');

INSERT INTO app_users (id, password)
SELECT 'shiho', 'shiho'
WHERE NOT EXISTS (SELECT 1 FROM app_users WHERE id = 'shiho');
