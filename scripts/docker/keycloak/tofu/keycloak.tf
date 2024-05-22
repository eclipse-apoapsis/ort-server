terraform {
  required_providers {
    keycloak = {
      source  = "mrparkers/keycloak"
      version = "4.4.0"
    }
  }
}

provider "keycloak" {
  client_id = "admin-cli"
  username  = "admin"
  password  = "admin"
  url       = "http://keycloak:8080"
}

resource "keycloak_realm" "realm" {
  realm   = "master"
  enabled = true
}

resource "keycloak_openid_client" "ort_server" {
  realm_id  = keycloak_realm.realm.id
  client_id = "ort-server"
  name      = "ORT Server"
  enabled   = true

  access_type         = "PUBLIC"
  valid_redirect_uris = [
    "http://localhost:8080/*",
    "http://localhost:8081/*"
  ]

  frontchannel_logout_enabled  = true
  direct_access_grants_enabled = true
  standard_flow_enabled        = true
}

resource "keycloak_openid_client_scope" "ort_server_client_scope" {
  realm_id               = keycloak_realm.realm.id
  name                   = "ort-server-client"
  description            = "Shared scope for clients interacting with the ORT Server"
  include_in_token_scope = false
  gui_order              = 1
}

resource "keycloak_openid_audience_protocol_mapper" "ort_server_client" {
  realm_id        = keycloak_realm.realm.id
  client_scope_id = keycloak_openid_client_scope.ort_server_client_scope.id
  name            = "ORT-server-audience-mapper"

  included_client_audience = keycloak_openid_client.ort_server.client_id
}

resource "keycloak_openid_client_default_scopes" "ort_server_default_scopes" {
  realm_id  = keycloak_realm.realm.id
  client_id = keycloak_openid_client.ort_server.id

  default_scopes = [
    "web-origins",
    "acr",
    "roles",
    "profile",
    "email",
    keycloak_openid_client_scope.ort_server_client_scope.name,
  ]
}

resource "keycloak_role" "superuser" {
  realm_id    = keycloak_realm.realm.id
  client_id   = keycloak_openid_client.ort_server.id
  name        = "superuser"
  description = "This role is auto-generated, do not edit or remove."
}

resource "keycloak_generic_role_mapper" "client_role_mapper" {
  realm_id  = keycloak_realm.realm.id
  client_id = keycloak_openid_client.ort_server.id
  role_id   = keycloak_role.superuser.id
}

resource "keycloak_group" "superusers" {
  realm_id = keycloak_realm.realm.id
  name     = "SUPERUSERS"
}

resource "keycloak_user" "ort_admin" {
  realm_id = keycloak_realm.realm.id
  username = "ort-admin"
  enabled  = true

  email      = "ort-admin@example.com"
  first_name = "ORT"
  last_name  = "Admin"

  initial_password {
    value     = "admin"
    temporary = false
  }
}

resource "keycloak_group_memberships" "superusers_members" {
  realm_id = keycloak_realm.realm.id
  group_id = keycloak_group.superusers.id

  members = [
    keycloak_user.ort_admin.username
  ]
}

resource "keycloak_openid_client" "react" {
  realm_id  = keycloak_realm.realm.id
  client_id = "react"
  enabled   = true

  access_type                     = "PUBLIC"
  root_url                        = "http://localhost:5173"
  base_url                        = "http://localhost:5173"
  valid_redirect_uris             = ["/*"]
  valid_post_logout_redirect_uris = ["/*"]
  web_origins                     = ["+"]
  access_token_lifespan           = 300
  standard_flow_enabled           = true
  direct_access_grants_enabled    = true
  frontchannel_logout_enabled     = true
}
