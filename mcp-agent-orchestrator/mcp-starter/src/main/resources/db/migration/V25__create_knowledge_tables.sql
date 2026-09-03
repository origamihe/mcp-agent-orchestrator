-- Attempt to create pgvector extension (optional, will fail silently if not available)
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS vector;
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'pgvector extension not available, embedding will be stored as text';
END $$;

CREATE TABLE IF NOT EXISTS mcp_agent.knowledge_collections (
    id              VARCHAR(64)     NOT NULL PRIMARY KEY,
    name            VARCHAR(300)    NOT NULL,
    description     TEXT,
    embedding_model VARCHAR(100),
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS mcp_agent.knowledge_documents (
    id              VARCHAR(64)     NOT NULL PRIMARY KEY,
    collection_id   VARCHAR(64)     NOT NULL,
    title           VARCHAR(500)    NOT NULL,
    source          VARCHAR(1000),
    format          VARCHAR(50)     NOT NULL DEFAULT 'text',
    size            BIGINT          DEFAULT 0,
    metadata        JSONB,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_knowledge_docs_collection FOREIGN KEY (collection_id)
        REFERENCES mcp_agent.knowledge_collections(id) ON DELETE CASCADE
);

-- Create knowledge_chunks with conditional embedding type:
-- Uses vector(1536) if pgvector is available, falls back to TEXT otherwise
DO $$
DECLARE
    has_vector boolean;
BEGIN
    SELECT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'vector') INTO has_vector;

    IF has_vector THEN
        EXECUTE '
            CREATE TABLE IF NOT EXISTS mcp_agent.knowledge_chunks (
                id              VARCHAR(64)     NOT NULL PRIMARY KEY,
                document_id     VARCHAR(64)     NOT NULL,
                content         TEXT            NOT NULL,
                embedding       vector(1536),
                chunk_index     INTEGER         NOT NULL DEFAULT 0,
                metadata        JSONB,
                created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT fk_knowledge_chunks_doc FOREIGN KEY (document_id)
                    REFERENCES mcp_agent.knowledge_documents(id) ON DELETE CASCADE
            )';
    ELSE
        EXECUTE '
            CREATE TABLE IF NOT EXISTS mcp_agent.knowledge_chunks (
                id              VARCHAR(64)     NOT NULL PRIMARY KEY,
                document_id     VARCHAR(64)     NOT NULL,
                content         TEXT            NOT NULL,
                embedding       TEXT,
                chunk_index     INTEGER         NOT NULL DEFAULT 0,
                metadata        JSONB,
                created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT fk_knowledge_chunks_doc FOREIGN KEY (document_id)
                    REFERENCES mcp_agent.knowledge_documents(id) ON DELETE CASCADE
            )';
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_knowledge_docs_collection  ON mcp_agent.knowledge_documents(collection_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_chunks_doc        ON mcp_agent.knowledge_chunks(document_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_chunks_index      ON mcp_agent.knowledge_chunks(document_id, chunk_index);