CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(200) UNIQUE NOT NULL,
    password_hash VARCHAR(200) NOT NULL,
    created_at TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS work (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    title VARCHAR(200) NOT NULL,
    genre VARCHAR(100),
    created_at TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS chapter (
    id BIGSERIAL PRIMARY KEY,
    work_id BIGINT REFERENCES work(id),
    seq INT NOT NULL,
    title VARCHAR(200),
    status VARCHAR(20) DEFAULT 'draft',
    created_at TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS character (
    id BIGSERIAL PRIMARY KEY,
    work_id BIGINT REFERENCES work(id),
    name VARCHAR(100) NOT NULL,
    aliases JSONB,
    role VARCHAR(50),
    ability TEXT,
    first_appear_chapter INT,
    status VARCHAR(50),
    created_at TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS world_setting (
    id BIGSERIAL PRIMARY KEY,
    work_id BIGINT REFERENCES work(id),
    type VARCHAR(50),
    content TEXT NOT NULL,
    established_chapter INT,
    version INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS timeline_event (
    id BIGSERIAL PRIMARY KEY,
    work_id BIGINT REFERENCES work(id),
    time_point VARCHAR(100),
    event TEXT,
    involved_characters JSONB,
    chapter INT,
    created_at TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS foreshadow (
    id BIGSERIAL PRIMARY KEY,
    work_id BIGINT REFERENCES work(id),
    content TEXT NOT NULL,
    planted_chapter INT,
    resolved_chapter INT,
    status VARCHAR(20) DEFAULT 'open',
    created_at TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS consistency_report (
    id BIGSERIAL PRIMARY KEY,
    work_id BIGINT REFERENCES work(id),
    chapter_id BIGINT REFERENCES chapter(id),
    report_json JSONB,
    created_at TIMESTAMP DEFAULT now()
);