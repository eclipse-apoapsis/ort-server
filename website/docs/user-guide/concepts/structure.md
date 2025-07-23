# Structure

This document describes how ORT Server is structured.

## Entities

ORT Server groups runs by repository, product, and organization.
Each organization can have multiple products, and each product can have multiple repositories, and each repository can have multiple runs.

### Organizations

Organizations are a root entity. Admins of an organization can manage:

- products
- repositories
- users
- secrets
- infrastructure services

### Products

Products are an entity to group repositories within an organization. Admins of a product can manage:

- repositories
- users
- secrets

### Repositories

A repository represents a VCS repository, for example, a Git repository on GitHub. Admins of a repository can manage:

- users
- secrets
- runs

## Users

To manage user rights, they can be assigned roles directly (see also [Authorization](../../admin-guide/architecture/authorization.md)) or indirectly by being members of a group.

### Groups

Groups have assigned roles. Users in the group inherit all roles from the group.
Each group contains the roles of its child entity, e.g. the _readers_ group on product level also contains the _readers_ group for the child repositories.

#### Readers

Users in the _readers_ group can see the details of an entity and the child entities.
On repository level, they can see ORT run results.

#### Writers

Users in the _writers_ group have the roles of the _readers_ group.
Additionally, they can create child entities. On repository level, they can trigger new ORT runs.

#### Admins

Users in the _admins_ group have the roles of the _writers_ group.
Additionally, they can manage secrets, infrastructure services and user groups.
