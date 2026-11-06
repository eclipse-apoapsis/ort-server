-- Usernames can be given to a new user, while the subject ID of a user never changes. Replace the unique constraint
-- on the username with a plain index for lookups by name. The next migration renames the table to users, so the
-- index already gets the name here it will keep.

ALTER TABLE user_display_names DROP CONSTRAINT user_display_names_username_key;

CREATE INDEX users_username_idx ON user_display_names (username);
