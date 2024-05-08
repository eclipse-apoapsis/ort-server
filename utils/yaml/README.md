# YAML Utils

This module provides an abstraction for deserialization YAML input via
[Kotlin Serialization](https://github.com/Kotlin/kotlinx.serialization), which is based on the
[KAML](https://github.com/charleskorn/kaml) library.

While the functionality provided here is rather trivial, the main reason for introducing this module is to address a 
conflict between the dependencies of the `KAML` library and other parts of the ORT Server codebase: `KAML` drags in
a version of [SnakeYaml Engine](https://bitbucket.org/snakeyaml/snakeyaml-engine/src/master/) that is incompatible with
the version required by [JRuby](https://www.jruby.org/). As a result, Analyzer fails with an exception when processing
Ruby projects. The conflict is solved by using the [Gradle Shadow plugin](https://github.com/johnrengelman/shadow) to
bundle the dependencies of `KAML` into the Jar archive of this module and relocating the conflicting classes to a new
package structure.

This approach works, but is certainly not an ideal solution. Conflicts between the dependencies of plugins and
ORT Server/ORT classes could occur again in the future, and introducing a wrapper module for each affected library
would not scale. So, a long-term solution would be to enforce an isolation of the classpaths of plugins from the rest
of the codebase. There is an [issue for ORT](https://github.com/oss-review-toolkit/ort/issues/6798) covering this
topic. Possible solutions could be:

* Applying the Gradle Shadow plugin to all plugins, so that they are integrated as fat Jars which are loaded by a
  special class loader. A drawback of this approach is the increased size of the plugins and the resulting container
  images.
* Using a custom class loader for plugins with some clever delegation logic that loads standard classes from the
  ORT Server classpath, but allows plugins to override some of their dependencies.
