# GitHub config file provider

This module provides an implementation of the `ConfigFileProvider` interface defined by the [Configuration abstraction](../README.md) that reads configuration files from GitHub.

## Synopsis

The implementation is located in the [GitHubConfigFileProvider](src/main/kotlin/GitHubConfigFileProvider.kt) class.
The provider is using GitHub REST API to access the configuration files stored in repositories.

The provided context is supposed to contain the branch name to use, if any.
If the context is empty, any branch name configured via `gitHubDefaultBranch` is used.
If no default branch name is configured, the repository's default branch as configured on GitHub is used.
Finally, as the last fallback, "main" is tried as the branch name.
Then the `resolveContext` function is used to resolve the SHA-1 of the last commit on the branch, which can later be used to ensure that the same configuration is used for the whole ORT run.

The `listFiles` function can be used to get the list of all the objects of type `file` located in the given path.
It requires the provided path to refer to a directory, otherwise a `ConfigException` exception is thrown.

In order to make sure that a configuration file is present in the given path, the `contains` function can be used.
It accepts a branch name and a path to a file and returns `true` if the file is present or `false` if the returned object is not a file, or if the specified path does not exist in the given repository at all.

The `getFile` function allows to download a file from the provided path and branch.
This function sends a GET request to GitHub API with the header `Accept` set to GitHub’s custom content type `application/vnd.github.raw` in order to receive a raw content of the referenced file.
If the provided path refers a directory, GitHub API will ignore the `Accept` header and return a JSON array with the directory content.
In this case, as well as in the case when the returned 'Content Type' header is neither one of `application/vnd.github.raw` or `application/json`, or it is missing, a \[ConfigException\] is thrown with the description of the cause.

## Configuration

In order to activate `GitHubConfigFileProvider`, the application configuration must contain a section `configManager` with a property `fileProvider` set to the value "github-config".
In addition, there are several configuration properties required by the provider.
The fragment below shows an example:

```
configManager {
  fileProvider = "github-config"
  gitHubApiUrl = "https://api.github.com"
  gitHubRepositoryOwner = "ownername"
  gitHubRepositoryName = "reponame"
  gitHubDefaultBranch = "config"
}
```

This table contains a description of the supported configuration properties:

| Property              | Description                                                                                                                                                                                                                                                                                             | Default                  | Secret |
|-----------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------|--------|
| gitHubApiUrl          | Defines the base URL of the GitHub REST API. Typically, this property does not need to be specified, since the default value should work.                                                                                                                                                               | <https://api.github.com> | no     |
| gitHubRepositoryOwner | The name of the owner of the repository that contains the configuration information. This corresponds to the `OWNER` parameter of the GitHub REST API.                                                                                                                                                  | none                     | no     |
| gitHubRepositoryName  | The name of the repository that contains the configuration information. Together with the `gitHubRepositoryOwner` property, the repository is uniquely identified. This corresponds to the `REPO` parameter of the GitHub REST API.                                                                     | none                     | no     |
| gitHubDefaultBranch   | The default branch in the repository that contains the configuration information. Users can select a specific branch by passing a corresponding `Context` to the `resolveContext()` function. If here the default context is provided, this provider implementation uses the configured default branch. | main                     | no     |
| gitHubApiToken        | The personal access token to authorize against the GitHub REST API.                                                                                                                                                                                                                                     | none                     | yes    |

The provider implementation is using the Bearer Token authorization.
The token is obtained from the `ConfigSecretProvider` via the `gitHubApiToken` path.
For the details on GitHub API authorization see the [Documentation on Authenticating to the GitHub REST API](https://docs.github.com/en/rest/overview/authenticating-to-the-rest-api?apiVersion=2022-11-28).
