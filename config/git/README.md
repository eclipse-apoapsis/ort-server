# Git config file provider

This module provides an implementation of the `ConfigFileProvider` interface defined by the [Configuration abstraction](../README.md) that reads configuration files from a git repository.
The provider is capable of resolving files, directories, symbolic links and submodules in the repository.

## Synopsis

The implementation is located in the [GitConfigFileProvider](src/main/kotlin/GitConfigFileProvider.kt) class.
The provider is using ORT Downloader's VersionControlSystem to check out the configuration repository from git to a temporary local folder that exists for the lifetime of a job.

The implementation is using the provided context to make sure that the branch specified in it is present in the repository.
If the branch is present, the `resolveContext` function returns the SHA-1 ID of the last commit in the branch, which can later be utilized to make sure that the same configuration is used for the whole ORT run.
Note that
- `resolveContext` is called only once per run (by the config worker), and the returned context is used for all workers in the run
- in case `resolveContext` is called with empty input context, the head of the default branch of the repository is returned

The `listFiles` function can be used to get the list of all the objects of type `file` located in the given path.
It takes in the context (as returned by the `resolveContext` function) and requires the provided path to refer to a directory, otherwise a `ConfigException` is thrown.

In order to make sure that a configuration file is present in the given path, the `contains` function can be used.
It takes in the context and a path to a file and returns `true` if the file is present or `false` if the returned object is not a file, or if the specified path does not exist in the given repository at all.

The `getFile` function returns an `InputStream` for reading the content of a configuration file with the given `path` in the given `context`, throwing an exception if the file cannot be resolved or access is not possible for whatever reasons.

## Configuration

### Enabling the provider (static configuration)

In order to activate `GitConfigFileProvider`, the application configuration must contain a section `configManager` with a property `fileProvider` set to the value "git-config".
In addition, the URL of the git repository is needed, for example:

```
configManager {
  fileProvider = "git-config"
  gitUrl = "https://config.repository.git"
}
```
In order to avoid hard-coding the configuration of the provider in the `application.conf` files, environment variables should be used, for example
```
configManager {
  fileProvider = ${?ANALYZER_CONFIG_FILE_PROVIDER}
  gitUrl = ${?ANALYZER_CONFIG_GIT_URL}
}
```

The provider supports password authentication for private repositories using these configuration options:

| Option                     | Secret | Description                                      |
|----------------------------|--------|--------------------------------------------------|
| gitConfigFileProviderUser  | yes    | The username to use for authentication.          |
| gitConfigFileProviderToken | yes    | The token or password to use for authentication. |

### Runtime configuration

When creating an ORT Run, the API accepts a `jobConfigContext` parameter, which is passed as an input to config worker's `resolveContext`,
so by passing the branch name as `jobConfigContext`, the provider will use the specified branch for all workers.
In case no `jobConfigContext` is provided, the provider will use the default branch of the repository.

Because the git configuration file provider is capable of returning files using their relative paths inside the configuration repository, this gives flexibility to specify several configuration parameters when creating jobs of a run. Examples of such are:

```
"evaluator": {
  "copyrightGarbageFile": "customer1/product1/copyright-garbage.yml",
  "licenseClassificationsFile": "common/license-classifications.yml",
  "resolutionsFile": "common/resolutions.yml",
  "ruleSet": "customer1/product1/rules.evaluator.kts"
},
```

In this example, all configuration files are located in their respective directories in the repository.
Note that for example customer-specific rules could have many versions in their respective branches, and by varying the `jobConfigContext` parameter, different versions of the rules can be used in jobs.