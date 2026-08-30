---
position: 3
---

# Releasing

## Semantic versioning

The version of a new release is determined by the types of the commit messages since the previous release, following the [semantic versioning](https://semver.org/) scheme.
For this, the [git-semver-plugin](https://github.com/jmongard/Git.SemVersioning.Gradle) for Gradle is used.
Its documentation provides a [good overview](https://github.com/jmongard/Git.SemVersioning.Gradle?tab=readme-ov-file#example-of-how-version-is-calculated) of how the version is determined.

## Creating a release

To create a new release tag, run

```sh
./gradlew releaseVersion
```

to create a local release tag in Git.
Committers can then execute `git push origin <tag>` to push the tag to the remote repository, which triggers the release workflow on GitHub.
