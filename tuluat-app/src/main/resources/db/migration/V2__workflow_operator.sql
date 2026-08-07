-- Enable vector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Table for Workflow Sessions
CREATE TABLE IF NOT EXISTS workflow_sessions (
    session_id UUID PRIMARY KEY,
    workflow_name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    current_node_id VARCHAR(255),
    loop_count INT NOT NULL DEFAULT 0,
    context_data JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Table for Session Short Memory (Chat History)
CREATE TABLE IF NOT EXISTS session_short_memory (
    id BIGSERIAL PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES workflow_sessions(session_id) ON DELETE CASCADE,
    agent_name VARCHAR(255),
    role VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Table for Session Long Memory (Pgvector Semantic Store)
CREATE TABLE IF NOT EXISTS session_long_memory (
    id BIGSERIAL PRIMARY KEY,
    session_id UUID REFERENCES workflow_sessions(session_id) ON DELETE SET NULL,
    workflow_name VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1536),
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_workflow_sessions_name ON workflow_sessions(workflow_name);
CREATE INDEX IF NOT EXISTS idx_short_memory_session ON session_short_memory(session_id);
