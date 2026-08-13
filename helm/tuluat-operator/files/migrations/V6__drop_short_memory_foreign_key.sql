-- Short-term conversation memory is used both by workflow sessions and by
-- multi-turn chat sessions. Chat sessionIds are client-generated and have no
-- corresponding workflow_sessions row, so the FK on session_id blocks every
-- chat memory write. Drop it.
ALTER TABLE session_short_memory DROP CONSTRAINT IF EXISTS session_short_memory_session_id_fkey;
