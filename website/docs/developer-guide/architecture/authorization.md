# Authorization

This document describes how ORT Server controls access to its resources, and how to use the authorization component when implementing new features.

## Overview

ORT Server separates authentication from authorization:

- **Authentication** is handled by [Keycloak](https://www.keycloak.org/).
  Clients authenticate using [OpenID Connect](https://openid.net/developers/how-connect-works/) and send the resulting JSON Web Token (JWT) with each request.
  Keycloak also stores the user accounts.
- **Authorization** is handled by ORT Server itself.
  The roles assigned to users are stored in the ORT Server database, and ORT Server evaluates them for each request.

Keycloak does not store any roles, permissions, or groups for ORT Server.
Users are identified by their username, which ORT Server reads from the `preferred_username` claim of the JWT.

## Concepts

### Hierarchy

Resources in ORT Server are organized in a hierarchy of organizations, products, and repositories.
Each organization contains products, and each product contains repositories.
Access rights are defined on the elements of this hierarchy.

### Permissions

A permission allows a user to perform a specific action on a hierarchy element.
Permissions are defined separately for each hierarchy level.

**Organization permissions:**

| Permission               | Allows the user to                                          |
| ------------------------ | ----------------------------------------------------------- |
| `READ`                   | Read the organization details.                              |
| `WRITE`                  | Change the organization details.                            |
| `WRITE_SECRETS`          | Manage the secrets of the organization.                     |
| `MANAGE_GROUPS`          | Assign roles on the organization.                           |
| `MANAGE_DEFAULT_PLUGINS` | Manage the plugins enabled by default for the organization. |
| `READ_PRODUCTS`          | List the products of the organization.                      |
| `CREATE_PRODUCT`         | Create products in the organization.                        |
| `DELETE`                 | Delete the organization.                                    |

**Product permissions:**

| Permission          | Allows the user to                                    |
| ------------------- | ----------------------------------------------------- |
| `READ`              | Read the product details.                             |
| `WRITE`             | Change the product details.                           |
| `WRITE_SECRETS`     | Manage the secrets of the product.                    |
| `MANAGE_GROUPS`     | Assign roles on the product.                          |
| `READ_REPOSITORIES` | List the repositories of the product.                 |
| `CREATE_REPOSITORY` | Create repositories in the product.                   |
| `TRIGGER_ORT_RUN`   | Trigger ORT runs for the repositories of the product. |
| `DELETE`            | Delete the product.                                   |

**Repository permissions:**

| Permission           | Allows the user to                            |
| -------------------- | --------------------------------------------- |
| `READ`               | Read the repository details.                  |
| `WRITE`              | Change the repository details.                |
| `WRITE_SECRETS`      | Manage the secrets of the repository.         |
| `MANAGE_GROUPS`      | Assign roles on the repository.               |
| `MANAGE_RESOLUTIONS` | Manage the resolutions of the repository.     |
| `READ_ORT_RUNS`      | Read the ORT runs of the repository.          |
| `TRIGGER_ORT_RUN`    | Trigger ORT runs for the repository.          |
| `DELETE`             | Delete the repository or one of its ORT runs. |

### Roles

Permissions are not assigned to users directly.
Instead, users are assigned one of three roles on a hierarchy element: `READER`, `WRITER`, or `ADMIN`.
Each role grants a fixed set of permissions:

| Role     | Permissions on the assigned element                                                                                                                    |
| -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `READER` | Read access: `READ` and the permission to list child elements or ORT runs.                                                                             |
| `WRITER` | All `READER` permissions, plus `WRITE`, `WRITE_SECRETS`, creating child elements, and triggering ORT runs. On repositories, also `MANAGE_RESOLUTIONS`. |
| `ADMIN`  | All permissions, including `MANAGE_GROUPS`, `MANAGE_DEFAULT_PLUGINS`, and `DELETE`.                                                                    |

A user can have at most one role on each hierarchy element.
Assigning a new role on an element replaces the previous one.

The roles cannot be customized.
They are defined as enums in the authorization component, see [Implementation](#implementation).

### Inheritance

Roles affect not only the element they are assigned on, but also other levels of the hierarchy.
The following rules apply:

- **Roles are inherited downwards.**
  A role on an organization grants the same role on all its products and repositories.
  A role on a product grants the same role on all its repositories.
  For example, a user with the `WRITER` role on an organization has write access to all repositories of that organization.
- **Roles on lower levels can extend inherited permissions, but cannot restrict them.**
  For example, a user with the `WRITER` role on a product can be an `ADMIN` of one of its repositories.
  A `READER` role on that repository, however, has no effect, because the user already inherits the `WRITER` role.
- **Roles on lower levels grant read access to the parent elements.**
  A user with any role on a repository can read the parent product and organization.
  This implicit read access does not inherit downwards: it does not grant access to the other products and repositories of the organization.

### Superusers

A superuser has all permissions on all hierarchy elements.
Some operations require superuser rights, for example, creating organizations, managing users, and setting the `keepAliveWorker` flag for ORT runs.

Internally, superuser rights are stored as an assignment of the organization `ADMIN` role on the wildcard hierarchy ID `CompoundHierarchyId.WILDCARD`.

## Implementation

The authorization logic is located in the [`components/authorization`](https://github.com/eclipse-apoapsis/ort-server/tree/main/components/authorization) module:

- `api-model` contains the roles, permissions, and user information as exposed by the REST API.
- `backend` contains the role and permission definitions, the services, and the Ktor integration.

The most important classes are:

| Class                                                                 | Purpose                                                                                 |
| --------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| `OrganizationPermission`, `ProductPermission`, `RepositoryPermission` | Define the permissions on each hierarchy level.                                         |
| `OrganizationRole`, `ProductRole`, `RepositoryRole`                   | Define the roles and the permissions they grant on all levels.                          |
| `RoleAssignmentsTable`                                                | Stores the role assignments in the `role_assignments` database table.                   |
| `AuthorizationService`                                                | Checks permissions, manages role assignments, and creates filters for database queries. |
| `HierarchyPermissions`                                                | Implements the [inheritance rules](#inheritance).                                       |
| `EffectiveRole`                                                       | Holds the permissions of a user on a specific hierarchy element.                        |
| `UserService`                                                         | Manages the user accounts in Keycloak.                                                  |
| `OrtServerPrincipal`                                                  | Represents the authenticated user of a request, including the effective role.           |

### Authentication

`configureAuthentication()` sets up the Ktor JWT authentication.
It validates the token against the issuer and the JSON Web Key Set configured in the `jwt` section of the [core configuration](https://github.com/eclipse-apoapsis/ort-server/blob/main/core/src/main/resources/application.conf).
The token must contain the audience configured in `jwt.audience`.
In Keycloak, add an audience mapper to the client scope to include it.

For each authenticated request, ORT Server creates an `OrtServerPrincipal`.
If the route requires a permission, the principal also contains the `EffectiveRole` of the user on the requested hierarchy element.

### Protecting Routes

To protect a route, pass an `AuthorizationChecker` to the route function.
The authorization component provides overloads of the Ktor route functions `get`, `post`, `put`, `patch`, and `delete` that accept a checker.
Create checkers with the following functions:

- `requirePermission(permission)` checks a permission on the hierarchy element identified by the path parameter `organizationId`, `productId`, or `repositoryId`, depending on the type of the permission.
- `requireSuperuser()` checks that the user is a superuser.

Within the route handler, `call.ortServerPrincipal.effectiveRole` provides the permissions of the user and the ID of the hierarchy element.
For example:

```kotlin
route("repositories/{repositoryId}") {
    delete(deleteRepository, requirePermission(RepositoryPermission.DELETE)) {
        val effectiveRole = call.ortServerPrincipal.effectiveRole
        // ...
    }
}
```

The check results in the following HTTP status codes:

| Situation                                           | Status code        |
| --------------------------------------------------- | ------------------ |
| The request does not contain a valid token.         | `401 Unauthorized` |
| The user does not have the required permission.     | `403 Forbidden`    |
| The hierarchy element from the path does not exist. | `404 Not Found`    |

:::warning
Use the hierarchy ID from `effectiveRole.elementId` for further authorization calls.
The `AuthorizationService` functions that accept a `CompoundHierarchyId` do not verify that the contained IDs belong together.
Passing an ID constructed from unverified request parameters can grant access to elements the user is not allowed to access.
:::

### Filtering Lists

Endpoints that return lists of hierarchy elements, such as all organizations a user can access, cannot check a single element.
Instead, they use the standard route functions without a checker, and filter the query results:

1. Call `AuthorizationService.filterHierarchyIds()` with the user ID and the required role or permissions.
   The function returns a `HierarchyFilter` that describes all elements the user can access, taking the [inheritance rules](#inheritance) into account.
   For superusers, it returns a wildcard filter.
2. Pass the filter to the repository function that performs the query.
   The DAO layer converts it into a database condition with `HierarchyFilter.apply()`.

For an example, see `OrganizationService.listOrganizationsForUser()`.

### Managing Role Assignments

Use `AuthorizationService` to change role assignments:

- `assignRole()` assigns a role to a user on a hierarchy element.
- `removeAssignment()` removes the role of a user on a hierarchy element.
- `removeAssignments()` removes all role assignments on a hierarchy element, and optionally on its child elements.
  For example, `RepositoryService` calls it when a repository is moved to another product, because the inherited roles change.
- `removeUserAssignments()` removes all role assignments of a user.
  `UserService.deleteUser()` calls it before deleting a user.

When a hierarchy element is deleted, the database removes its role assignments automatically.

The following role assignments are created automatically:

- The creator of a product receives the `ADMIN` role on the product.
- The creator of a repository receives the `ADMIN` role on the repository.

The REST API provides the following endpoints to manage roles, relative to the base path `/api/v1`:

| Endpoint                                                       | Required permission          |
| -------------------------------------------------------------- | ---------------------------- |
| `PUT`, `DELETE` `/organizations/{organizationId}/roles/{role}` | Organization `MANAGE_GROUPS` |
| `PUT`, `DELETE` `/products/{productId}/roles/{role}`           | Product `MANAGE_GROUPS`      |
| `PUT`, `DELETE` `/repositories/{repositoryId}/roles/{role}`    | Repository `MANAGE_GROUPS`   |
| `PUT`, `DELETE` `/admin/users/{username}/superuser`            | Superuser                    |

### Exposing Permissions to the UI

The endpoint `GET /api/v1/authorization/userinfo` returns the permissions of the current user on the hierarchy element identified by the query parameters `organizationId`, `productId`, or `repositoryId`, and whether the user is a superuser.
The UI uses this information to show or hide actions.
It provides the components in `ui/src/components/authorization` for this purpose.

:::note
Hiding an action in the UI does not protect it.
Every endpoint must perform its own authorization check.
:::

### Testing

To test the authorization of an endpoint, derive the test class from `AbstractAuthorizationTest` in `shared/ktor-utils`.
It provides a database and a Keycloak instance, and the following helper functions:

- `requestShouldRequireAuthentication()` verifies that a request fails without a valid token.
- `requestShouldRequireRole()` verifies that a request requires a specific role on a hierarchy element.
- `requestShouldRequireSuperuser()` verifies that a request requires superuser rights.

## Migration from Keycloak-Based Authorization

Earlier versions of ORT Server stored roles and permissions as roles and groups in Keycloak.
When the core service starts and the `role_assignments` table is empty, `RolesToDbMigration` copies the existing assignments to the database:

- Members of the groups for organizations, products, and repositories, such as `ORGANIZATION_1_READERS`, receive the corresponding role.
- Members of the `SUPERUSERS` group become superusers.

The migration considers the group name prefix configured for Keycloak.
After the migration, ORT Server no longer reads or updates these groups.

:::note
The migration also runs on a new installation, because the `role_assignments` table is empty.
As a result, the members of the `SUPERUSERS` group in Keycloak become the initial superusers.
:::
