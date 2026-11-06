-- Rename the table of user display names to users, as other tables will reference it.

ALTER TABLE user_display_names RENAME TO users;

ALTER TABLE users RENAME CONSTRAINT user_display_names_pkey TO users_pkey;
