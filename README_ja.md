# <img alt="ORT Server" src="website/static/img/ort-server-logo.svg" width="10%"> Eclipse Apoapsis™ – ORT Server

<!-- hy-mt2-i18n:start -->
[English](./README.md) | [中文](./README_zh-CN.md) | **日本語** | [Español](./README_es.md)
<!-- hy-mt2-i18n:end -->


[Eclipse Apoapsis](https://projects.eclipse.org/projects/technology.apoapsis) プロジェクトの**ORT Server**は、[OSS Review Toolkit](https://github.com/oss-review-toolkit/ort) をクラウド上でサービスとして展開するためのスタンドアロンアプリケーションです。

> [!NOTE]
> このプロジェクトは現在、Eclipse Foundationにおいて[インキュベーション段階](https://www.eclipse.org/projects/handbook/#incubation)にあり、
> 初回リリースの実現に向けて取り組んでいます。
> リリースされるとセマンティックバージョニングが採用されますが、それまではいつでも破壊的な変更が行われる可能性があります。
> 
> <img alt="Eclipse Incubation" src="https://projects.eclipse.org/modules/custom/eclipsefdn/eclipsefdn_projects/images/project_state/incubating.png" width="10%">

## コミュニティ

開発者と連絡を取り合うには、以下の方法があります：
* [Matrixチャット](https://matrix.to/#/#apoapsis:matrix.eclipse.org)に参加する。
* GitHubの[ディスカッション](https://github.com/eclipse-apoapsis/ort-server/discussions)を開始する。
* [メーリングリスト](https://accounts.eclipse.org/mailing-list/apoapsis-dev)に参加する。

問題が発生した場合は、[issue tracker](https://github.com/eclipse-apoapsis/ort-server/issues)に報告してください。

ご貢献を歓迎します。詳細については、[コントリビューションガイド](CONTRIBUTING.md)をご覧ください。

## ORT Serverの実行方法

テスト用にORT Serverを実行する最も簡単な方法は、[Docker Compose](https://docs.docker.com/compose/)を使用することです。
Kubernetes上での適切なデプロイメントのために、今後このプロジェクトではHelmチャートが提供される予定です。

### Docker Composeの使用方法

> [!CAUTION]
> TLSを使用せずにKeycloakなどを提供するなど、複数の不安定なデフォルト設定が含まれているため、本番環境ではDocker Composeのセットアップを使用しないでください。

必要なサードパーティ製サービスを使用してORT Serverを起動するには、次のコマンドを実行してください：

```shell
docker compose up
```

サービスが起動し稼働していれば、[http://localhost:8082](http://localhost:8082) で ORT Server の UI にアクセスできます。

詳細な手順、例えばローカル画像を使用してDocker Composeを実行する方法については、[ドキュメント](https://eclipse-apoapsis.github.io/ort-server/docs/admin-guide/getting-started/docker-compose)をご覧ください。

## Dockerイメージの公開

レジストリにDockerイメージを公開するには、まず[ドキュメント](https://eclipse-apoapsis.github.io/ort-server/docs/admin-guide/getting-started/docker-compose/#run-with-local-images)に記載されている通り、ワーカーベースイメージをビルドします。その後、レジストリ用の適切なプレフィックスを設定し、`jib`タスクを使用してイメージを公開できます。また、デフォルトが`latest`のタグも設定可能です。

```shell
# すべてのDockerイメージを公開する。
./gradlew -PdockerImagePrefix=my.registry/ tinyJibPublish

# 特定のイメージのみを公開する。
./gradlew -PdockerImagePrefix=my.registry/ :core:tinyJibPublish

# カスタムタグを使用して公開する。
./gradlew -PdockerImagePrefix=my.registry/ -PdockerImageTag=custom tinyJibPublish
```

## OpenAPI仕様の生成

このGradleタスクを実行することでOpenAPI仕様を生成できます：

```shell
./gradlew :core:generateOpenApiSpec
```

このタスクは、仕様を `ui/build/openapi.json` に書き出します。

## ライセンス

著作権に関する詳細は、このプロジェクトのルートにある[NOTICE](./NOTICE)ファイルをご覧ください。

ライセンスに関する詳細は、このプロジェクトのルートにある[LICENSE](./LICENSE)ファイルをご覧ください。
