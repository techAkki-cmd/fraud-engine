CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;
CREATE EXTENSION IF NOT EXISTS hstore WITH SCHEMA public;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;

CREATE SCHEMA IF NOT EXISTS fraud_agent;

CREATE TABLE IF NOT EXISTS public.fraud_profile_vectors (
    id UUID PRIMARY KEY,
    content TEXT,
    metadata JSON,
    embedding vector(768)
);

CREATE INDEX IF NOT EXISTS fraud_profile_vectors_index
    ON public.fraud_profile_vectors USING hnsw (embedding vector_cosine_ops);

CREATE TABLE fraud_agent.fraud_decisions (
    payment_id UUID PRIMARY KEY,
    status VARCHAR(16) NOT NULL,
    decision VARCHAR(16),
    reasoning VARCHAR(512),
    correlation_id VARCHAR(128) NOT NULL,
    source_topic VARCHAR(128) NOT NULL,
    source_partition INTEGER NOT NULL,
    source_offset BIGINT NOT NULL,
    model VARCHAR(128),
    vector_matches TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_fraud_decisions_status CHECK (status IN ('PROCESSING', 'COMPLETED')),
    CONSTRAINT ck_fraud_decisions_decision CHECK (decision IS NULL OR decision IN ('SAFE', 'FRAUD')),
    CONSTRAINT ck_fraud_decisions_completed_fields CHECK (
        (status = 'PROCESSING' AND decision IS NULL AND reasoning IS NULL)
        OR
        (status = 'COMPLETED' AND decision IS NOT NULL AND reasoning IS NOT NULL AND model IS NOT NULL)
    )
);

CREATE INDEX ix_fraud_decisions_source_record
    ON fraud_agent.fraud_decisions (source_topic, source_partition, source_offset);
