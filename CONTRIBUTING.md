# Contributing Guide

## Eclipse Contributor Agreement (ECA)

Before your contributions can be accepted, you have to electronically sign the [Eclipse Contributor Agreement](https://www.eclipse.org/legal/ECA.php).
This is a one-time process that should take only a few minutes.
For more details on the ECA also see the [ECA FAQ](https://www.eclipse.org/legal/ecafaq.php).

It is automatically checked in PRs if you have signed the ECA.
For this to work, you need to use the same email in Git that you also used to create your Eclipse Foundation account. 

### Becoming a committer

In Eclipse Foundation terminology, everyone who contributes to a project is a contributor.
Committers are contributors who have write access to the repository.
You can learn more about project roles in the [Eclipse Project Handbook](https://www.eclipse.org/projects/handbook/#roles).

To become a committer, you need to be nominated by an existing committer, followed by a vote among the existing committers.
The election process is described in the [Eclipse Project Handbook](https://www.eclipse.org/projects/handbook/#elections-committer).

Before you can be nominated as a committer, you need to have a history of high-quality contributions to the project.
As contributions can be made in many forms, such as code, documentation, or community building, there is no fixed definition of what constitutes sufficient contributions.
Instead, decisions are made on a case-by-case basis by the existing committers.
Therefore, committer nominations have to provide a specific rationalization for why the nominee should be granted committer status.
More examples can be found in the [wiki](https://wiki.eclipse.org/Technology) of the Eclipse Technology top-level project that Eclipse Apoapsis belongs to. 

## Creating Pull Requests

To create a pull request, you have to fork the repository and create a new branch as described in the [GitHub documentation](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/creating-a-pull-request-from-a-fork).

This project puts high value on the structure of pull requests to make the review process as efficient as possible.
The following sections describe the guidelines for creating pull requests.
Please have a look at the Git history of the repository and already merged PRs to get a feeling for how contributions are expected to be made.

### Atomic commits

The commits in a PR should be atomic, meaning that each commit should represent a single logical change.
For example, refactorings should be in separate commits from bug fixes.
This makes it easier to understand the changes and to review them.
It also makes it easier to revert changes if necessary.

Commits should also be self-contained, meaning that they should pass all tests and not break the build.

### Commit messages

Commit messages should be written in the imperative mood.
For example, use "Add feature" instead of "Added feature".
Also, they should be written in impersonal form, meaning that you should not use terms like "I" or "we".

This project requires that commit messages for the [conventional commits](https://www.conventionalcommits.org/) specification.
This means that each commit message should follow the format `<type>(<scope>): <description>`.
The description should start with a capital letter and not end with a period.

> The length of the description line shall not exceed 75 characters.

#### Types

The valid types are defined in the [Commitlint configuration file](.commitlintrc.yml).
When chosing a type, please consider the following guidelines:

* `build`: Changes that affect the build system.
* `chore`: Changes that do not affect the application, e.g., removing an obsolete workaround in the code.
* `ci`: Changes to the CI configuration.
* `deps`: Changes to the dependencies of the project.
* `docs`: Changes to the documentation, either in code or in documentation files.
* `feat`: A new feature of the application.
* `fix`: A bug fix in the application.
* `perf`: A change that improves the performance of the application.
* `refactor`: A code refactoring that does not change the behavior of the application.
* `revert`: A commit that reverts a previous commit.
* `style`: Changes that do not affect the meaning of the code, e.g., formatting changes.
* `test`: Changes to the tests of the application.

The types `fix`, `feat`, and `perf` are reserved for changes that affect the application and should not be used for changes that are not user-facing.
For example, a new build system feature should use `build`, and a test fix should use `test`.
This is important because the commit message titles are used to generate the release notes, and the feature and fix sections in the release notes should only contain user-facing changes.

#### Scopes

The scopes should represent the part of the project that was changed, e.g., `analyzer`, `core`, or `ui`.
This usually matches the name of the Gradle module that was changed.
If a commit touches multiple parts of the project, and it is not possible to split it into multiple commits, the scope can be omitted.

#### Breaking changes

Breaking changes should be indicated by either adding a `BREAKING CHANGE:` section to the commit message body or by adding a `!` after the type, for example:

```
feat(api)!: Change the API to use a different format
```

or

```
feat(api): Change the API to use a different format

BREAKING CHANGE: The API now uses a different format.
```

> [!CAUTION]
> Before the 1.0.0 release, breaking changes can occur at any time and should not be marked as such in the commit message.
> Otherwise, they would change the major version from 0 to 1 which is not intended before the 1.0.0 release (see [Semantic versioning](#semantic-versioning)).

#### Body

The body of the commit message should contain a more detailed description of the change.
Especially, it should explain **why** the change was made and how it affects the application.

> The length of each line in the body shall not exceed 75 characters.

#### Linking GitHub issues

If a commit is related to a GitHub issue, it should be linked in the commit message footer, for example:

```
fix(api): Fix a bug

Some more detailed description of the fix.

Fixes #123.
```

This will automatically close the issue when the commit is merged to the main branch.
In addition to the commit message, the link can optionally be added to the PR description.

For the full list of supported keywords see the [GitHub documentation](https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/linking-a-pull-request-to-an-issue#linking-a-pull-request-to-an-issue-using-a-keyword).

If the commit is related to an issue without fixing it, it should still be referenced, for example, with `Relates to #123.`

#### Sign-off

Commit body shall end with a sign-off line specifying the real name and email 
of the committer. Example:
```
Signed-off-by: Firstname Lastname <first.last@example.com>
```

#### Validation

All commit messages are validated by the [commitlint](https://commitlint.js.org/) tool in CI.
You can locally verify that your commit messages pass the checks by running `npx commitlint --from=HEAD~1`.
The number at the end specifies how many commits back you want to check.

#### Examples

If you are unsure how to properly write a commit message, just have a look at the commit history of this repository.

### Semantic versioning

The version of a new release is determined by the types of the commit messages since the previous release, following the [semantic versioning](https://semver.org/) scheme.
For this, the [git-semver-plugin](https://github.com/jmongard/Git.SemVersioning.Gradle) for Gradle is used.
Its documentation provides a [good overview](https://github.com/jmongard/Git.SemVersioning.Gradle?tab=readme-ov-file#example-of-how-version-is-calculated) of how the version is determined.

### Code review workflow

Code review comments should be addressed in the commit they belong to.
So instead of creating a new commit to address the comments, amend the existing commit.
For this, you should make yourself familiar with the `git commit --amend` and `git rebase --interactive` commands.
After fixing your commit, make a force push to your branch.

Please respond to all comments in the PR to make it easier for the reviewers to see that their comments have been addressed.
If you applied a suggestion, you can also give a thumbs up reaction to the comment to signal that it has been addressed.
Also, please click the "Re-request review" button next to the reviewer names on the top-right after you have addressed all comments to signal that the PR is ready for review again.

### Code style

The two main languages used in this project are [Kotlin](https://kotlinlang.org/) and [TypeScript](https://www.typescriptlang.org/).
For both languages, there are PR checks that enforce a consistent code style.
It is advised to run those checks locally before creating a PR.

For Kotlin, you can run `./gradlew detekt detektMain detektTest` to run the static code analysis.
You can also force `detect` to automatically fix some issues (for example, import order) by running `./gradlew detekt --auto-correct`.

For TypeScript, you can run `pnpm format:check` within the `ui/` and `/website` directories.

### Larger changes

For larger changes, it is advised to [open an issue](https://github.com/eclipse-apoapsis/ort-server/issues/new) first to discuss the changes to prevent wasted effort.
Alternatively, you can also start a [GitHub discussion](https://github.com/eclipse-apoapsis/ort-server/discussions).
