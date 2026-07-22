-- F92: вид статуса для деривации цвета из роли (WORK / REVIEW / WAITING).
ALTER TABLE status_mappings ADD COLUMN IF NOT EXISTS status_kind VARCHAR(16);

-- Одноразовый seed по имени (data-fix; дальше правится в wizard).
UPDATE status_mappings SET status_kind = 'REVIEW'
WHERE status_category = 'IN_PROGRESS'
  AND (LOWER(jira_status_name) LIKE '%review%'
       OR LOWER(jira_status_name) LIKE '%ревью%'
       OR LOWER(jira_status_name) LIKE '%проверк%');

UPDATE status_mappings SET status_kind = 'WAITING'
WHERE status_category = 'IN_PROGRESS' AND status_kind IS NULL
  AND (LOWER(jira_status_name) LIKE '%waiting%'
       OR LOWER(jira_status_name) LIKE '%ожидан%');

UPDATE status_mappings SET status_kind = 'WORK'
WHERE status_category = 'IN_PROGRESS' AND status_kind IS NULL;

-- Дефолтные категорийные цвета становятся derived (NULL); ручные выживают.
UPDATE status_mappings SET color = NULL
WHERE UPPER(color) IN ('#DFE1E6', '#DEEBFF', '#E3FCEF', '#E6FCFF', '#EAE6FF');
