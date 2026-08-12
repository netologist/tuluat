-- Table for Per-Node Execution Metrics (T3: kubectl workflowsessions node executions)
CREATE TABLE IF NOT EXISTS workflow_session_node_executions (
    id BIGSERIAL PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES workflow_sessions(session_id) ON DELETE CASCADE,
    node_id VARCHAR(255) NOT NULL,
    agent_name VARCHAR(255),
    provider VARCHAR(255),
    model VARCHAR(255),
    input_prompt TEXT,
    output_text TEXT,
    start_time TIMESTAMP WITH TIME ZONE,
    end_time TIMESTAMP WITH TIME ZONE,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    total_tokens BIGINT NOT NULL DEFAULT 0,
    input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    cost_usd NUMERIC(20, 6) NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'COMPLETED',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_node_sessions_session_id ON workflow_session_node_executions(session_id);
CREATE INDEX IF NOT EXISTS idx_node_sessions_node_id ON workflow_session_node_executions(node_id);
