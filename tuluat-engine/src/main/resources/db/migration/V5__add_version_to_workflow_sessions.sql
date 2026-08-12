-- Add optimistic locking version column to workflow_sessions (ADR 001)
ALTER TABLE workflow_sessions ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
