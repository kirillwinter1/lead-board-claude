-- F54: Semantic search support (pgvector)
-- Conditional migration: skips if pgvector extension is not available (e.g., on production managed PostgreSQL)
-- In multi-tenant setup, jira_issues exists only in tenant schemas, so ALTER TABLE is conditional
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'vector') THEN
    CREATE EXTENSION IF NOT EXISTS vector;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'jira_issues' AND table_schema = current_schema()) THEN
      ALTER TABLE jira_issues ADD COLUMN IF NOT EXISTS embedding vector(1536);
      CREATE INDEX IF NOT EXISTS idx_jira_issues_embedding
        ON jira_issues USING hnsw(embedding vector_cosine_ops);
    END IF;
  END IF;
END $$;
