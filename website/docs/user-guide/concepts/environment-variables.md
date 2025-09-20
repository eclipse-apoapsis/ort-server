# Environment Variables

In the [Analyzer](https://oss-review-toolkit.org/ort/#analyzer)
step of the [OSS Review Toolkit](https://oss-review-toolkit.org/ort/) pipeline,
dependencies of your project are identified, and
information about them is gathered, such as declared license, authors and source code location.

In detail, package managers perform this task. As in most cases it is **not** appropriate to configure these package
managers by files that are located in the source code repository of your project, typically environment variables
are used by CI/CD pipelines and also by the OSS Review Toolkit pipeline.

## General usage

To define these environment variables, use the `.ort.env.yml` file in your project repository. This also
ensures that the configuration is version controlled and provides full traceability for changes over time.

There are two ways to define environment variables in the `.ort.env.yml` file:

- For **sensitive** values, use a secret reference that references to a value in your _Secrets Provider_.
  Of course, the reference must exist in your _Secrets Provider_ (e.g. HashiCorp Vault).
  These references are resolved just before the scan runs.
- For **insensitive** values, you can define the value directly in the `.ort.env.yml` file.

### Example `.ort.env.yml`

```yaml
environmentVariables:
  - name: 'MY_SENSITIVE_VALUE'
    secretName: 'my-secret-reference'
  - name: 'MY_INSENSITIVE_VALUE'
    value: 'This is not a secret'
```

The value for environment variable `MY_SENSITIVE_VALUE` is retrieved from the _Secrets Provider_ using the reference `my-secret-reference`.

## Usage with Gradle

When using Gradle as your build system, use environemnt variables to directly inject values into the Gradle
build process as _project properties_. This is the preferred way to pass secrets / tokens into CI builds,
because you don’t have to commit them into `gradle.properties`.

In contrast to plain environment variables, this approach integrates with Gradle’s property resolution system.
You can access these _project properties_ with `project.findProperty("myProp")`.
Plugins and conventions that rely on _project properties_ can pick them up automatically.

To use this feature, prefix the name of the environment variable with `ORG_GRADLE_PROJECT_`.

For more information about setting a project property in Gradle via environment variables, see the
[Gradle documentation](https://docs.gradle.org/current/userguide/build_environment.html#setting_a_project_property).

### Example `.ort.env.yml`

```yaml
environmentVariables:
  - name: 'ORG_GRADLE_PROJECT_web_server_username'
    value: 'abc123'
  - name: 'ORG_GRADLE_PROJECT_web_server_password'
    secretName: 'my-web-server-password-reference'
```

The value for environment variable `ORG_GRADLE_PROJECT_web_server_password` is retrieved from the _Secrets Provider_ using the reference `my-web-server-password-reference`.

You can access these _project properties_ in your `build.gradle` file like this:

```groovy
def web_server_username = project.findProperty("web_server_username")
def web_server_password = project.findProperty("web_server_password")
```
