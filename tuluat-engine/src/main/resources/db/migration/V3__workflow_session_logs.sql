-- Table for Session Execution Logs
CREATE TABLE IF NOT EXISTS workflow_session_logs (
    id BIGSERIAL PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES workflow_sessions(session_id) ON DELETE CASCADE,
    node_id VARCHAR(255),
    log_level VARCHAR(20) NOT NULL DEFAULT 'INFO',
    message TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_session_logs_session ON workflow_session_logs(session_id);
