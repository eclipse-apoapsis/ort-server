# <img alt="ORT Server" src="website/static/img/ort-server-logo.svg" width="1%"> Eclipse Apoapsis™ - ORT Server

<!-- hy-mt2-i18n:start -->
[English](./README.md) | **中文** | [日本語](./README_ja.md) | [Español](./README_es.md)
<!-- hy-mt2-i18n:end -->


[Eclipse Apoapsis](https://projects.eclipse.org/projects/technology.apoapsis) 项目中的**ORT Server**是一款独立应用程序，可用于在云端将[OSS Review Toolkit](https://github.com/oss-review-toolkit/ort)作为服务进行部署。

> [!NOTE]
> 该项目目前处于 Eclipse 基金会的[孵化阶段](https://www.eclipse.org/projects/handbook/#incubation)，正在努力推出首个版本。正式发布后该项目将采用语义化版本控制，在此之前随时都可能出现破坏性变更。
> 
> <img alt="Eclipse Incubation" src="https://projects.eclipse.org/modules/custom/eclipsefdn/eclipsefdn_projects/images/project_state/incubating.png" width="10%">

## 社区

若需与开发者沟通，您可以：
* 加入 [Matrix 聊天群组](https://matrix.to/#/#apoapsis:matrix.eclipse.org)。
* 在 GitHub 上发起[讨论](https://github.com/eclipse-apoapsis/ort-server/discussions)。
* 参与[邮件列表](https://accounts.eclipse.org/mailing-list/apoapsis-dev)。

如有任何问题，请在[问题追踪器](https://github.com/eclipse-apoapsis/ort-server/issues)中报告。

欢迎大家贡献代码，更多详情请参阅[贡献指南](CONTRIBUTING.md)。

## 运行 ORT Server

测试时运行 ORT Server 最简单的方法是使用 [Docker Compose](https://docs.docker.com/compose/)。若要将其正确部署到 Kubernetes 中，该项目日后会提供 Helm chart。

### Docker Compose 设置

> [!CAUTION]
> 请勿在生产环境中使用 Docker Compose 配置，因为它采用了多种不安全的默认设置，例如在未启用 TLS 的情况下就使用 Keycloak。

要启动包含所需第三方服务的ORT Server，请运行：

```shell
docker compose up
```

当所有服务启动并运行后，您可以通过 [http://localhost:8082](http://localhost:8082) 访问 ORT Server 的用户界面。

详细操作说明，例如如何使用本地镜像运行 Docker Compose，可参阅[文档](https://eclipse-apoapsis.github.io/ort-server/docs/admin-guide/getting-started/docker-compose)。

## 发布 Docker 镜像

若要将 Docker 镜像发布到注册表中，首先需按照[文档](https://eclipse-apoapsis.github.io/ort-server/docs/admin-guide/getting-started/docker-compose/#run-with-local-images)中的说明构建工作节点基础镜像。之后即可通过设置正确的注册表前缀，使用 `jib` 任务来发布这些镜像。同时也可配置镜像标签，其默认值为 `latest`。

```shell
# 发布所有 Docker 镜像。
./gradlew -PdockerImagePrefix=my.registry/ tinyJibPublish

# 发布某个特定的镜像。
./gradlew -PdockerImagePrefix=my.registry/ :core:tinyJibPublish

# 使用自定义标签发布镜像。
./gradlew -PdockerImagePrefix=my.registry/ -PdockerImageTag=custom tinyJibPublish
```

## 生成 OpenAPI 规范文档

运行该 Gradle 任务即可生成 OpenAPI 规范：

```shell
./gradlew :core:generateOpenApiSpec
```

该任务会将规范内容写入 `ui/build/openapi.json` 文件中。

## 许可证

有关版权详情，请查看该项目根目录下的 [NOTICE](./NOTICE) 文件。

有关许可证的详细信息，请查看该项目根目录下的 [LICENSE](./LICENSE) 文件。
