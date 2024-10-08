-- This migration script maps aliases for Git VCS types to the 'GIT' type itself.

UPDATE vcs_info
SET type = 'GIT'
WHERE UPPER(type) = 'GITHUB';
