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

## Users and Roles

Access to organizations, products, and repositories is controlled by roles.
A user can be assigned one role on each organization, product, or repository.
Assigning a new role replaces the previous role of the user on that entity.

For technical details, see the [Authorization](../../developer-guide/architecture/authorization.md) documentation.

### Available Roles

| Role       | Allows the user to                                                                                                                                                                            |
| ---------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Reader** | View the entity and its child entities. On repositories, view the results of ORT runs.                                                                                                        |
| **Writer** | Everything a reader can do. Additionally, edit the entity, manage secrets and infrastructure services, create child entities, and trigger ORT runs. On repositories, also manage resolutions. |
| **Admin**  | Everything a writer can do. Additionally, assign roles to other users and delete the entity. On organizations, also manage the plugins that are enabled by default.                           |

A user who creates a product or repository automatically becomes an admin of it.

### How Roles Apply to Child Entities

Roles apply to the entity they are assigned on and to all its child entities:

- A role on an organization applies to all products and repositories of the organization.
- A role on a product applies to all repositories of the product.

A role on a child entity can extend the rights a user has from a parent entity, but cannot reduce them.
For example, a writer of a product can be made an admin of one of its repositories.
Assigning the reader role on that repository has no effect, because the user is already a writer of it through the product.

A user with a role on a product or repository can also view its parent entities.
This does not give the user access to the other products and repositories of the organization.

### Managing Roles

Admins assign roles on the _Users_ page of an organization, product, or repository.

### Superusers

Superusers are administrators of the ORT Server instance.
They have all rights on all entities.
Only superusers can create organizations and manage user accounts.
