# Website

This website is built using [Docusaurus](https://docusaurus.io/), a modern static website generator.

## Build OpenAPI specification

Before you can build the website, you need to build the OpenAPI specification by running this command in the root of the repository:

```
$ ./gradlew :core:generateOpenApiSpec
```

## Installation

```
$ pnpm install --frozen-lockfile
```

## Local Development

Before starting the local development server, you need to generate the API docs by running:

```
$ pnpm gen-api-docs
```

Then you can run:

```
$ pnpm start
```

This command starts a local development server and opens up a browser window. Most changes are reflected live without having to restart the server.

## Build

```
$ pnpm build
```

This command generates static content into the `build` directory and can be served using any static contents hosting service.

## Deployment

Using SSH:

```
$ USE_SSH=true pnpm deploy
```

Not using SSH:

```
$ GIT_USER=<Your GitHub username> pnpm deploy
```

If you are using GitHub pages for hosting, this command is a convenient way to build the website and push to the `gh-pages` branch.
