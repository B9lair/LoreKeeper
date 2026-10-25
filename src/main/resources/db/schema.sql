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

-- ==================== 步骤 2.4 数据库变更（方案 A）====================
-- 目标库：LoreKeeper_DB（云 RDS PostgreSQL）
-- 幂等脚本，可重复执行

-- ① world_setting 加四列，承载 NewSetting(entity/attribute/value/evidence) 四元组与证据锚定
ALTER TABLE public.world_setting
    ADD COLUMN IF NOT EXISTS entity VARCHAR(100),
    ADD COLUMN IF NOT EXISTS attribute VARCHAR(100),
    ADD COLUMN IF NOT EXISTS value TEXT,
    ADD COLUMN IF NOT EXISTS evidence TEXT;

-- ② 归并/比对高频按 (work_id, entity) 查，加索引
CREATE INDEX IF NOT EXISTS idx_world_setting_work_entity
    ON public.world_setting (work_id, entity);

-- ③ 验证：确认新列已加上
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name = 'world_setting'
ORDER BY ordinal_position;