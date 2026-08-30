---
position: 2
---

# Contributing

See the [contributing guide](https://github.com/eclipse-apoapsis/ort-server/?tab=contributing-ov-file) for general information on how to contribute to the ORT Server project.

## Code style

The two main languages used in this project are [Kotlin](https://kotlinlang.org/) and [TypeScript](https://www.typescriptlang.org/).
For both languages, there are PR checks that enforce a consistent code style.
It is advised to run those checks locally before creating a PR.

For Kotlin, you can run `./gradlew detektAll` to run the static code analysis.
You can also force `detekt` to automatically fix some issues (for example, import order) by running `./gradlew detekt --auto-correct`.

For TypeScript, you can run `pnpm format:check` within the `ui/` and `/website` directories to check the code style.
You can also automatically fix issues by running `pnpm format` within the respective directories.

## Commit messages

Commit messages need to follow the [conventional commit](https://www.conventionalcommits.org/) specification as the version of a new release is determined by the types of the commit messages since the previous release.
Please refer to the respective section in the [contributing guide](https://github.com/eclipse-apoapsis/ort-server/?tab=contributing-ov-file#commit-messages) for details.
