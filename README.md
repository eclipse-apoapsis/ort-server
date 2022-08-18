# ORT Server

The ORT server is a standalone application to deploy the 
[OSS Review Toolkit](https://github.com/oss-review-toolkit/ort) as a service in the cloud.

## Local Setup

To start the ORT server with the required 3rd party services, you can use
[Docker Compose](https://docs.docker.com/compose/). Start the required containers using docker:
```shell
docker compose up
```
Do not use the Docker Compose setup in production as it uses multiple insecure defaults, like providing KeyCloak without
TLS.

# License

See the [NOTICE](./NOTICE) file in the root of this project for the copyright details.

See the [LICENSE](./LICENSE) file in the root of this project for license details.

OSS Review Toolkit (ORT) is a [Linux Foundation project](https://www.linuxfoundation.org) and part of
[ACT](https://automatecompliance.org/).
