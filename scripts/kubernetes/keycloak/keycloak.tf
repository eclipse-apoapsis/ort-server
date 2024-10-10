terraform {
  required_providers {
    keycloak = {
      source = "mrparkers/keycloak"
      version = "4.4.0"
    }
  }
}

provider "keycloak" {
    client_id     = "admin-cli"
    username      = "keycloak-admin"
    password      = "keycloak-admin"
    url           = "http://localhost:8081"
}

resource "keycloak_realm" "realm" {
  realm   = "ort-server"
  enabled = true
}

data "keycloak_openid_client" "realm_management" {
  realm_id  = keycloak_realm.realm.id
  client_id = "realm-management"
}

data "keycloak_role" "create-client" {
  realm_id  = keycloak_realm.realm.id
  client_id = data.keycloak_openid_client.realm_management.id
  name      = "create-client"
}

data "keycloak_role" "impersonation" {
  realm_id  = keycloak_realm.realm.id
  client_id = data.keycloak_openid_client.realm_management.id
  name      = "impersonation"
}

data "keycloak_role" "manage-authorization" {
  realm_id  = keycloak_realm.realm.id
  client_id = data.keycloak_openid_client.realm_management.id
  name      = "manage-authorization"
}

data "keycloak_role" "manage-clients" {
  realm_id  = keycloak_realm.realm.id
  client_id = data.keycloak_openid_client.realm_management.id
  name      = "manage-clients"
}

data "keycloak_role" "manage-events" {
  realm_id  = keycloak_realm.realm.id
  client_id = data.keycloak_openid_client.realm_management.id
  name      = "manage-events"
}

data "keycloak_role" "manage-realm" {
  realm_id  = keycloak_realm.realm.id
  client_id = data.keycloak_openid_client.realm_management.id
  name      = "manage-realm"
}

data "keycloak_role" "manage-users" {
  realm_id  = keycloak_realm.realm.id
  client_id = data.keycloak_openid_client.realm_management.id
  name      = "manage-users"
}

data "keycloak_role" "query-clients" {
  realm_id  = keycloak_realm.realm.id
  client_id = data.keycloak_openid_client.realm_management.id
  name      = "query-clients"
}

data "keycloak_role" "query-groups" {
  realm_id  = keycloak_realm.realm.id
  client_id = data.keycloak_openid_client.realm_management.id
  name      = "query-groups"
}

resource "keycloak_role" "admin" {
  realm_id    = keycloak_realm.realm.id
  name        = "admin"
  description = "Admin"
  composite_roles = [
    data.keycloak_role.create-client.id,
    data.keycloak_role.impersonation.id,
    data.keycloak_role.manage-authorization.id,
    data.keycloak_role.manage-clients.id,
    data.keycloak_role.manage-events.id,
    data.keycloak_role.manage-realm.id,
    data.keycloak_role.manage-users.id,
    data.keycloak_role.query-clients.id,
    data.keycloak_role.query-groups.id,
  ]
}

resource "keycloak_openid_client" "openid_client" {
  realm_id            = keycloak_realm.realm.id
  client_id           = "ort-server"
  name                = "ORT Server"
  enabled             = true

  access_type         = "PUBLIC"
  valid_redirect_uris = [
    "http://localhost:8080/*",
    "http://localhost:8081/*",
    "http://localhost:5173/*"
  ]
  valid_post_logout_redirect_uris = [
    "+"
  ]

  web_origins = [
    "*",
  ]

  standard_flow_enabled = true
  direct_access_grants_enabled = true
  frontchannel_logout_enabled = true
}

resource "keycloak_openid_client_scope" "openid_client_scope" {
  realm_id               = keycloak_realm.realm.id
  name                   = "ort-server-client"
  description            = "Shared scope for clients interacting with the ORT Server"
  include_in_token_scope = false
}

resource "keycloak_openid_audience_protocol_mapper" "audience_mapper" {
  realm_id        = keycloak_realm.realm.id
  client_scope_id = keycloak_openid_client_scope.openid_client_scope.id
  name            = "ORT-server-audience-mapper"

  included_client_audience = keycloak_openid_client.openid_client.client_id
  add_to_id_token = false
  add_to_access_token = true
}

resource "keycloak_openid_client_default_scopes" "client_default_scopes" {
  realm_id  = keycloak_realm.realm.id
  client_id = keycloak_openid_client.openid_client.id

  default_scopes = [
    "acr",
    "profile",
    "email",
    "roles",
    "web-origins",
    keycloak_openid_client_scope.openid_client_scope.name,
  ]
}

resource "keycloak_role" "superuser" {
  realm_id    = keycloak_realm.realm.id
  client_id   = keycloak_openid_client.openid_client.id
  name        = "superuser"
  description = "This role is auto-generated, do not edit or remove."
}

resource "keycloak_group" "superusers" {
  realm_id = keycloak_realm.realm.id
  name     = "SUPERUSERS"
}

resource "keycloak_group_roles" "group_roles" {
  realm_id = keycloak_realm.realm.id
  group_id = keycloak_group.superusers.id

  role_ids = [
    keycloak_role.superuser.id,
    keycloak_role.superuser.id,
  ]
}

resource "keycloak_user" "ort_admin" {
  realm_id   = keycloak_realm.realm.id
  username   = "admin"
  enabled    = true

  email      = "admin@oss-review-toolkit.org"
  first_name = "Ort"
  last_name  = "Admin"

  initial_password {
    value     = "admin"
    temporary = false
  }
}

resource "keycloak_user_groups" "ort_admin_roles" {
  realm_id = keycloak_realm.realm.id
  user_id = keycloak_user.ort_admin.id

  group_ids  = [
    keycloak_group.superusers.id
  ]
}

resource "keycloak_user" "realm_admin" {
  realm_id   = keycloak_realm.realm.id
  username   = "keycloak-admin"
  enabled    = true

  email      = "realm-admin@oss-review-toolkit.org"
  first_name = "Realm"
  last_name  = "Admin"

  initial_password {
    value     = "keycloak-admin"
    temporary = false
  }
}

resource "keycloak_user_roles" "realm_admin_roles" {
  realm_id = keycloak_realm.realm.id
  user_id  = keycloak_user.realm_admin.id

  role_ids = [
    keycloak_role.admin.id
  ]
}
