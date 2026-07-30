ALTER TABLE posts ADD COLUMN IF NOT EXISTS title varchar(100);
ALTER TABLE posts ADD COLUMN IF NOT EXISTS noticed_at timestamp;

UPDATE posts
SET title = left(trim(content), 100)
WHERE title IS NULL OR trim(title) = '';

UPDATE posts
SET noticed_at = coalesce(updated_at, created_at)
WHERE is_notice = true AND noticed_at IS NULL;

ALTER TABLE posts ALTER COLUMN title SET NOT NULL;
