# Contributing Guide

## Eclipse Contributor Agreement (ECA)

Before we can accept your contributions, you have to electronically sign the [Eclipse Contributor Agreement](https://www.eclipse.org/legal/ECA.php).
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

We put high value on the structure of pull requests to make the review process as efficient as possible.
The following sections describe the guidelines for creating pull requests.
Please have a look at the Git history of the repository and already merged PRs to get a feeling for how we expect contributions to be made.

### Atomic commits

The commits in a PR should be atomic, meaning that each commit should represent a single logical change.
For example, refactorings should be in separate commits from bug fixes.
This makes it easier to understand the changes and to review them.
It also makes it easier to revert changes if necessary.

Commits should also be self-contained, meaning that they should pass all tests and not break the build.

### Commit messages

We use [conventional commits](https://www.conventionalcommits.org/) for our commit messages.
This means that each commit message should follow the format `<type>(<scope>): <description>`.
The valid types are defined in the [Commitlint configuration file](.commitlintrc.yml).
The scopes should represent the part of the project that was changed, e.g., `analyzer`, `core`, or `ui`.
If a commit touches multiple parts of the project, and it is not possible to split it into multiple commits, the scope can be omitted.

Commit messages should be written in the imperative mood.
For example, use "Add feature" instead of "Added feature".

You can locally verify that your commit messages pass the checks by running `npx commitlint --from=HEAD~1`.
The number at the end specifies how many commits back you want to check.

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
For both languages, we have PR checks that enforce a consistent code style.
It is advised to run those checks locally before creating a PR.

For Kotlin, you can run `./gradlew detekt detektMain detektTest` to run the static code analysis.
For TypeScript, you can run `pnpm format:check` within the `ui/` directory.

### Larger changes

For larger changes, it is advised to [open an issue](https://github.com/eclipse-apoapsis/ort-server/issues/new) first to discuss the changes to prevent wasted effort.
Alternatively, you can also start a [GitHub discussion](https://github.com/eclipse-apoapsis/ort-server/discussions).
