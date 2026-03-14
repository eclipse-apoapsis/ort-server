-- Add a table to store encrypted secret values for the database secrets provider.
create table database_secrets
(
    path              text        not null,
    encrypted_value   text        not null,
    encryption_scheme varchar(32) not null,
    key_version       integer     not null,
    created_at        timestamp   not null default now(),
    updated_at        timestamp   not null default now(),

    primary key (path)
);
