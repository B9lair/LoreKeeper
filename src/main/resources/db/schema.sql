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


-- ① 关键词检索需要 pg_trgm（云 RDS 不支持则回退本地 Docker，见 B.4 第 4 条）
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS vector_store_content_trgm
    ON public.vector_store USING gin (content gin_trgm_ops);

-- ② 方案 B：chapter 表补整章原文列（2.4 上传章节时写入）
ALTER TABLE chapter ADD COLUMN IF NOT EXISTS content TEXT;