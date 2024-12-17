# Authorization

This document describes how authorization is implemented in the ORT Server.

## Keycloak

The ORT Server is integrated with [Keycloak](https://www.keycloak.org/) for identity and access management.
Therefore, all roles and permissions are managed by Keycloak, and the server defines which roles and permissions exist.
Keycloak itself knows only roles, therefore, all roles and permissions described below will be represented by roles in Keycloak.

## Entities

This section summarizes the entities that are relevant to the access management.

### Permission

A permission to perform a specific action or access a resource on the ORT Server.
It is implemented as a role in Keycloak.

### Role

A role is a group of permissions. It is implemented as a composite role in Keycloak.

### User

A single user that can log in to the ORT Server.
A user can have assigned roles or be member of groups.

### Group

A group of users that can have assigned roles.
All users in the group inherit all roles from the group.

## Specification

This specifies the available groups, roles, and permissions for organizations, products, and repositories in the ORT Server.

Organizations are a root entity used for grouping content in the ORT Server.
Each organization can have multiple products, and each product can have multiple repositories.
A repository represents a VCS repository, for example, a Git repository on GitHub.

For each of those entities, the ORT Server defines a set of fine-grained permissions which are grouped into reader, writer, and admin roles.
For each role, the ORT Server creates a group in Keycloak which makes it easier to manage the users that are assigned to the roles.

For now, the ORT Server supports a static role concept which might later be extended to support the customization of roles and permissions assigned to them.
It is possible to edit roles and groups in Keycloak, but the ORT Server needs to know the meaning of those roles and groups, so manual changes in Keycloak can lead to unexpected results.
Therefore, the ORT Server performs an automatic [synchronization](#keycloak-synchronization) of roles and groups.

Please note that the roles and permissions below are not complete and serve just as examples.
The currently defined roles and permissions can be looked up in the [enum classes](https://github.com/eclipse-apoapsis/ort-server/tree/main/model/src/commonMain/kotlin/authorization) where they are defined.

### Organizations

#### Permissions

| Name                | Function                                 | Name in Keycloak                           |
| ------------------- | ---------------------------------------- | ------------------------------------------ |
| READ                | Read organization details                | permission*organization*$id_read           |
| WRITE               | Write organization details               | permission*organization*$id_write          |
| READ_PRODUCTS       | Read list of products                    | permission*organization*$id_read_products  |
| CREATE_PRODUCT      | Create a new product in the organization | permission*organization*$id_create_product |
| DELETE_ORGANIZATION | Delete the organization                  | permission*organization*$id_delete         |

#### Roles

| Name   | Permissions                                | Name in Keycloak             | Group name in Keycloak    |
| ------ | ------------------------------------------ | ---------------------------- | ------------------------- |
| Reader | READ, READ_PRODUCTS                        | role*organization*$id_reader | ORGANIZATION\_$id_READERS |
| Writer | READ, READ_PRODUCTS, WRITE, CREATE_PRODUCT | role*organization*$id_writer | ORGANIZATION\_$id_WRITERS |
| Admin  | \*                                         | role*organization*$id_admin  | ORGANIZATION\_$id_ADMINS  |

### Products

#### Permissions

| Name              | Function                               | Name in Keycloak                         |
| ----------------- | -------------------------------------- | ---------------------------------------- |
| READ              | Read product details                   | permission*product*$id_read              |
| WRITE             | Write product details                  | permission*product*$id_write             |
| READ_REPOSITORIES | Read list of repositories              | permission*product*$id_read_repositories |
| CREATE_REPOSITORY | Create a new repository in the product | permission*product*$id_create_repository |
| DELETE_PRODUCT    | Delete the product                     | permission*product*$id_delete            |

#### Roles

| Name   | Permissions                                | Name in Keycloak        | Group name in Keycloak |
| ------ | ------------------------------------------ | ----------------------- | ---------------------- |
| Reader | READ, READ_REPOSITORIES                    | role*product*$id_reader | PRODUCT\_$id_READERS   |
| Writer | READ, READ_REPOSITORIES, CREATE_REPOSITORY | role*product*$id_writer | PRODUCT\_$id_WRITERS   |
| Admin  | \*                                         | role*product*$id_admin  | PRODUCT\_$id_ADMINS    |

### Repositories

#### Permissions

| Name              | Function                                 | Name in Keycloak                          |
| ----------------- | ---------------------------------------- | ----------------------------------------- |
| READ              | Read repository details                  | permission*repository*$id_read            |
| WRITE             | Write repository details                 | permission*repository*$id_write           |
| READ_ORT_RUNS     | Read scan results                        | permission*repository*$id_read_ort_runs   |
| TRIGGER_ORT_RUN   | Trigger a new ORT run for the repository | permission*repository*$id_trigger_ort_run |
| DELETE_REPOSITORY | Delete the repository                    | permission*repository*$id_delete          |

#### Roles

| Name   | Permissions                    | Name in Keycloak           | Group name in Keycloak  |
| ------ | ------------------------------ | -------------------------- | ----------------------- |
| Reader | READ, READ_SCANS               | role*repository*$id_reader | REPOSITORY\_$id_READERS |
| Writer | READ, READ_SCANS, TRIGGER_SCAN | role*repository*$id_writer | REPOSITORY\_$id_WRITERS |
| Admin  | \*                             | role*repository*$id_admin  | REPOSITORY\_$id_ADMINS  |

### Superuser

In addition to the roles and permissions defined above, the ORT Server also creates a "superuser" role and a "SUPERUSERS" group which can be used for server administrators.
Users with the "superuser" role have access to all resources.

### Hierarchy

The roles defined above are also hierarchical, that means that, for example, the reader role for an organization includes reader roles for all products in the organization, and the reader role for a product includes reader roles for all repositories in the product.

The idea behind this is to simplify checking for access permissions in the backend, because there are often multiple roles that can give access to a resource.
For example, an organization administrator is also an administrator for all products in the organization.
So, when performing an action on a product that requires admin permissions, the backend would have to check if the user is either an administrator for the product or an administrator for the organization.
With the hierarchical concept, it only needs to check for the specific permission, because the organization administrator role contains all product administrator roles and therefore also all permissions for all products within the organization.

### Examples

Below is a list of the roles and permissions for the following setup:

- Organization 1
  - Product 1
    - Repository 1

#### Roles

| Name                            | Permissions                                                                                                                                                                          | Contained Roles                 |
| ------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------- |
| role_organization_1_reader      | permission_organization_1_read, permission_organization_1_read_products                                                                                                              | role_product_1_reader           |
| role_organization_1_writer      | permission_organization_1_read, permission_organization_1_read_products, permission_organization_1_write, permission_organization_1_create_product                                   | role_product_1_writer           |
| role_organization_1_admin       | permission_organization_1_read, permission_organization_1_read_products, permission_organization_1_write, permission_organization_1_create_product, permission_organization_1_delete | role_product_1_admin            |
| role_product_1_reader           | permission_product_1_read, permission_product_1_read_repositories                                                                                                                    | role_repository_1_reader        |
| role_product_1_writer           | permission_product_1_read, permission_product_1_read_repositories, permission_product_1_write, permission_product_1_create_repository                                                | role_repository_1_writer        |
| product1_administrator          | permission_product_1_read, permission_product_1_read_repositories, permission_product_1_write, permission_product_1_create_repository, permission_product_1_delete                   | role_repository_1_administrator |
| role_repository_1_reader        | permission_repository_1_read, permission_repository_1_read_ort_runs                                                                                                                  |                                 |
| role_repository_1_writer        | permission_repository_1_read, permission_repository_1_read_ort_runs, permission_repository_1_writer, permission_repository_1_trigger_ort_run                                         |                                 |
| role_repository_1_administrator | permission_repository_1_read, permission_repository_1_read_ort_runs, permission_repository_1_writer, permission_repository_1_trigger_ort_run, permission_repository_1_delete         |                                 |

#### Groups

| Name                   | Contained Role             |
| ---------------------- | -------------------------- |
| ORGANIZATION_1_READERS | role_organization_1_reader |
| ORGANIZATION_1_WRITERS | role_organization_1_writer |
| ORGANIZATION_1_ADMINS  | role_organization_1_admin  |
| PRODUCT_1_READERS      | role_product_1_reader      |
| PRODUCT_1_WRITERS      | role_product_1_writer      |
| PRODUCT_1_ADMINS       | role_product_1_admin       |
| REPOSITORY_1_READERS   | role_repository_1_reader   |
| REPOSITORY_1_WRITERS   | role_repository_1_writer   |
| REPOSITORY_1_ADMINS    | role_repository_1_admin    |

## Authentication

Authentication is implemented using [OpenID Connect](https://openid.net/developers/how-connect-works/) (also see the [Keycloak specific docs](https://www.keycloak.org/docs/latest/securing_apps/#_oidc)).

The client roles of a user are not read from the JWT token, but are requested using the Keycloak API.
This has the benefit that all role changes in Keycloak are recognized immediately, not only when an access token is refreshed.
Another benefit is that the client roles do not have to be contained in the access token which makes it smaller.

An alternative to using the Keycloak API would have been to use the [userinfo endpoint](https://openid.net/specs/openid-connect-core-1_0.html#UserInfo).
However, the implementation would have been more complex, because access to the API was already implemented for the role and group management.

### Keycloak Configuration

The JWT token created by Keycloak must contain the audience configured in the `jwt.audience` configuration property of the [core module](https://github.com/eclipse-apoapsis/ort-server/blob/main/core/src/main/resources/application.conf).
For this, it is required to add an audience mapper to the client scope that adds the name of the audience to the JWT token.

By default, Keycloak creates a "roles" client scope that includes a "client roles" token mapper which adds all client roles of a user to the JWT token.
This can lead to very large JWT tokens if a user has many roles assigned, therefore, this mapper should be configured to not add the client roles to the access token.

## Access Management

While roles and groups should not be manually edited in Keycloak, the Keycloak UI is currently the only place where users can be granted permissions.
This might later be changed by introducing API endpoints to manage permissions, or by implementing a UI for this.

To manage user access, users should be added to the groups created for the entities.
For example, to give a user read access to the organization with the id "1", the user should be added to the group "ORGANIZATION_1_READERS".
Assigning roles directly to users is not recommended, because the role definitions could change with any update of the ORT Server, for example, by adding or removing permissions, or changing which permissions belong to a role.
If a user is added to a group, the ORT Server [ensures](#keycloak-synchronization) that the group always has the correct roles and permissions assigned.

Please note that it is possible to configure a prefix for group names, so the actual group names could be different to those used in this documentation.
For example, if the group name prefix is set to "PREFIX\_", the group from the previous paragraph would be called "PREFIX_ORGANIZATION_1_READERS".
The prefix option is useful in a testing setup where multiple instances of the ORT Server share the same Keycloak realm.

## Keycloak Synchronization

The ORT Server automatically synchronizes the defined roles and permissions with the roles and groups in Keycloak.
Currently, this happens when the core module is started.
This might later be extended by triggering synchronization periodically or by adding an endpoint to trigger synchronization manually.

Synchronization is required because:

- The role and permission definitions could have changed when upgrading to a newer version of the ORT Server.
- The roles and groups could have been manually changed in Keycloak, leading to unexpected results.

During synchronization, the ORT Server verifies that:

- All permissions for all hierarchy entities (organizations, products, and repositories) are represented by roles in Keycloak.
- All roles for all hierarchy entities are represented by composite roles in Keycloak and have the correct child roles assigned.
- All roles for all hierarchy entities are represented by groups in Keycloak and have the correct roles assigned.
