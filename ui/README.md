# Web UI

This directory contains the web UI for ORT Server.

## Architecture

The UI is a [React](https://react.dev/) application and uses [Vite](https://vitejs.dev/) as the build tool and [pnpm](https://pnpm.io/) as the package manager.

### React Compiler

The build runs [React Compiler](https://react.dev/learn/react-compiler) through `@rolldown/plugin-babel`, configured in `vite.config.ts`.
It memoizes components and hooks, so new code usually does not need `useMemo`, `useCallback` or `memo()`.
Keep a manual `useMemo` or `useCallback` where a value must keep its identity while the compiler would see a changed input, such as a new object with the same contents, and say why in a comment.

- The compiler silently skips a component it cannot handle, and `pnpm lint` does not report every reason for a skip; unsupported syntax such as `try … finally` is not reported.
  A skipped component also hides its other compiler lint findings.
- The compiler reuses a value computed from an object for as long as the object stays the same.
  A component that reads state through an object whose identity does not change when the state does, such as TanStack Table rows and columns, would show outdated content.
  Either read the state through an API that re-renders the component, such as `table.Subscribe`, or leave the component out with a `'use no memo'` directive and a comment saying why.
- With React Hook Form, a component that receives the result of `useForm` from its parent must subscribe to what it shows: read values with `useWatch` and form state with `useFormState`.
  `watch()` is rejected by lint.
  Leaving out only the child does not help, because the compiled parent keeps reusing the child's element.
  If the child cannot subscribe, leave out the parent instead, with a comment saying why.
  Call `getValues()` only in event handlers and effects.
- Define components at module level.
  For a component defined inside another function, the compiler can move callbacks out of the function, where they no longer see its variables.
- Code is compiled only in tests that run in the jsdom environment (`// @vitest-environment jsdom`).

## Prerequisites

By default, the UI expects ORT Server to be running locally.

If there are no local changes, the fastest way to get started is to use published ORT Server images for UI development.
To do so, run the following commands from the project root directory:

```shell
$ docker compose pull # Ensure that all images are up-to-date.
$ docker compose up -d # Bring services up in detached mode.
```

If you depend on local changes to the Kotlin backend, you instead need to build the images locally:

```shell
$ ./gradlew :buildAllImages # Build all images locally.
$ docker compose up -d # Bring services up in detached mode.
```

Next, ensure that the API definitions are up to date by running:

```shell
$ ./gradlew :core:generateOpenApiSpec
$ pnpm -C ui install
$ pnpm -C ui build
```

## Development

For interactive UI development with live-preview in the browser follow these steps:

1. Run `pnpm -C ui dev`.
2. Ctrl-click the shown `http://localhost:5173/` link.
3. Log in via Keycloak (use "admin" / "admin" as username / password).

### API Changes

If changes to the API were done during development, these are the minimum commands to rerun to reflect the changes (again from the project root):

```shell
$ ./gradlew -PdockerImagePrefix=ghcr.io/eclipse-apoapsis/ -PdockerImageTag=main :core:tinyJibDocker
$ docker compose up -d core
$ ./gradlew :core:generateOpenApiSpec
$ pnpm -C ui generate:api
```

## Unit and component tests

After completing the normal UI setup above, run the Vitest suite from the repository root:

```shell
$ pnpm -C ui test --run
$ pnpm -C ui test:coverage
```

These tests use local fixtures and mocks and do not require running Docker services or installing Playwright browsers.
Coverage is disabled for ordinary test runs. The coverage command prints statement, branch, function, and line totals and writes reports to `ui/coverage/`:

- Open `ui/coverage/index.html` in a browser to inspect coverage by source file.
- `ui/coverage/coverage-summary.json` contains machine-readable totals.
- `ui/coverage/lcov.info` can be used by coverage reporting tools.

Reports include untested TypeScript and TSX source files, but exclude generated API code, the generated route tree, and type declarations.
Generated reports are ignored by version control, ESLint, and Prettier. No coverage threshold is enforced.

See the [initial coverage baseline](tests/coverage-baseline.md) for the recorded results, exclusions, and limitations.

### Coverage in pull requests

The "Build and Test" workflow measures UI test coverage for every pull request and posts the result as a comment on it.
Each new push updates the same comment.
The comment compares the coverage of the pull request with the coverage of the `main` commit it targets, and shows the change in percentage points (pp): the plain difference between the two percentages.
The pull request is measured as GitHub merges it into `main` for testing, so the change comes from the pull request only, even if the branch is behind `main`.
The comment names the `main` commit and the last commit of the branch.

If the target commit is older than coverage reporting, the comment shows the coverage of the pull request only.
Measuring coverage makes the tests slower, so a test can occasionally hit its time limit.
Coverage is therefore measured in a separate run that cannot fail the build; if it fails for the pull request or the target commit, the comment says so.
Pull requests from forks get no permission to comment; their report is only on the summary page of the workflow run.
Pushes to `main` and merge queue runs show the current coverage on the summary page.

The full HTML and LCOV reports can be downloaded from the workflow run.
Coverage is for information only: a drop in coverage does not fail the build.
Small changes can come from code that depends on the current time, and the React Compiler can change function and branch counts, so look at the reports before reading too much into a change of a few hundredths.

## e2e tests

To run the Playwright e2e tests locally, first start core service (also starts keycloak, postgres and rabbitmq) and dev UI, then run the tests:

```shell
$ docker compose up -d core
$ cd ui
$ pnpm dev
$ pnpm test:e2e
```

## Docker

The Docker image for the UI is built as part of the `buildAllImages` Gradle task.
To build it manually, run the following steps from the root of the repository:

- Build the OpenAPI specification: `./gradlew :core:generateOpenApiSpec`
- Build the Docker image: `docker build -t ort-server-ui -f ui/docker/UI.Dockerfile ui`

To run the Docker image, use the following command:

```shell
docker run --rm -p 8082:8080 ort-server-ui
```

The Docker image can be configured by the following environment variables:

| Variable       | Default                               | Description                          |
| -------------- | ------------------------------------- | ------------------------------------ |
| `UI_API_URL`   | `http://localhost:8080`               | The URL of the ORT Server API.       |
| `UI_URL`       | `http://localhost:8082`               | The URL of the UI.                   |
| `UI_BASEPATH`  | `/`                                   | The base path of the UI.             |
| `UI_AUTHORITY` | `http://localhost:8081/realms/master` | The URL of the Keycloak realm.       |
| `UI_CLIENT_ID` | `ort-server-ui`                       | The client ID of the UI in Keycloak. |

### Read-only Root Filesystem

The image supports running with a read-only root filesystem (e.g. in OpenShift).
The following directories must be writable and should be mounted as `emptyDir` volumes:

| Path                    | Purpose                                 |
| ----------------------- | --------------------------------------- |
| `/etc/nginx/conf.d`     | Generated nginx config.                 |
| `/var/cache/nginx`      | nginx cache.                            |
| `/var/log/nginx`        | nginx logs.                             |
| `/usr/share/nginx/html` | Serving directory populated at startup. |
| `/var/run`              | nginx PID file.                         |
