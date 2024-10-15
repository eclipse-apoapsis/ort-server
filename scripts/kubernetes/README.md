# Setup

## Keycloak

Expoted the following clients from Docker Compose setup through the UI and imported them to the
Kubernetes setup through the UI:

1. ort-server
2. ort-server-ui
3. ort-server-ui-dev

Changed ort-server client authentication on and added "Service accounts roles" as authentication
flow. Created a client secret and added it the the manifest as "KEYCLOAK_API_SECRET". Added admin to
service accounts roles.

Create client scope "ort-server-client" with "Display on consent screen" off.

Configure mapper:

- Mapper type: Audience.
- Name: ORT-server-audience-mapping
- Included Client Audience: ort-server

Add the client scope to ort-server-ui-dev.
