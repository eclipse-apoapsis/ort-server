# Web UI

This directory contains the web UI for ORT Server.

## Architecture

The UI is a [React](https://react.dev/) application and uses [Vite](https://vitejs.dev/) as the
build tool and [pnpm](https://pnpm.io/) as the package manager.

## Development

The UI expects ORT Server to be running locally.

The UI currently requires manual creation of a Keycloak client. Go to <http://localhost:8081>, log
in with `admin:admin` and create a client with the following details:

1. Client ID: `react`
2. Root URL: `http://localhost:5173`
3. Home URL: `http://localhost:5173`
4. Valid redirect URIs: `/*`
5. Valid post logout redirect URIs: `/*`
6. Web origins: `+`
7. In the Advanced tab, set the "Access Token Lifespan" to expire in 5 minutes.
