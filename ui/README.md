# Web UI

This directory contains the web UI for ORT Server.

## Architecture

The UI is a [React](https://react.dev/) application and uses [Vite](https://vitejs.dev/) as the
build tool and [pnpm](https://pnpm.io/) as the package manager.

## Development

The UI expects ORT Server to be running locally.

## Run the UI

Here are the instructions to start the UI in local development mode:

1. Go to the `ui` folder.
2. Run `pnpm install` followed by `pnpm dev`.
3. Ctrl-click the shown `http://localhost:5173/` link.
4. Log in via Keycloak (use "admin" / "admin" as username / password).

## Regenerating the UI Query Client

The exact details and process of the synchronization between the ORT Server's OpenAPI specification and the matching UI queries is to be discussed.

In case there are changes in the OpenAPI specification that warrant regeneration of the query client used in the UI, here are the instructions to do so:

1. Go to the `ui/` folder
2. Run: `pnpm generate:api`
