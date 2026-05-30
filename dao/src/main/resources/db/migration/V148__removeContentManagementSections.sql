-- The only content management section is the footer, so migrate that to server_settings before dropping the
-- content_management_sections table.
INSERT INTO server_settings (key, value, is_enabled, updated_at)
SELECT 'FOOTER', markdown, is_enabled, updated_at
FROM content_management_sections
WHERE id = 'footer';

DROP TABLE content_management_sections;
