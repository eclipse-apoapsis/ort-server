-- Add a description column to the projects table because project descriptions were added to ORT.

ALTER TABLE projects ADD COLUMN description TEXT NOT NULL DEFAULT '';
