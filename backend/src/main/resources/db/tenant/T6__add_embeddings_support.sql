-- F54: Add embedding column to jira_issues in tenant schema
-- pgvector extension must already exist (created by V46 in public schema)
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
    ALTER TABLE jira_issues ADD COLUMN IF NOT EXISTS embedding vector(1536);
    CREATE INDEX IF NOT EXISTS idx_jira_issues_embedding
      ON jira_issues USING hnsw(embedding vector_cosine_ops);
  END IF;
END $$;
