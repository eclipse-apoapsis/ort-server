# <img alt="ORT Server" src="website/static/img/ort-server-logo.svg" width="1%"> Eclipse Apoapsis™ - Servidor ORT

<!-- hy-mt2-i18n:start -->
[English](./README.md) | [中文](./README_zh-CN.md) | [日本語](./README_ja.md) | **Español**
<!-- hy-mt2-i18n:end -->


El **ORT Server** del proyecto [Eclipse Apoapsis](https://projects.eclipse.org/projects/technology.apoapsis) es una aplicación independiente
para desplegar el [OSS Review Toolkit](https://github.com/oss-review-toolkit/ort) como servicio en la nube.

> [!NOTA]  
> Este proyecto se encuentra actualmente en la [fase de incubación](https://www.eclipse.org/projects/handbook/#incubation) en la
> Fundación Eclipse y está trabajando para lanzar su primera versión.  
> Una vez publicado, el proyecto utilizará la numeración semántica de versiones; hasta entonces, pueden producirse cambios que rompan la compatibilidad en cualquier momento.  
> 
> <img alt="Incubación en Eclipse" src="https://projects.eclipse.org/modules/custom/eclipsefdn/eclipsefdn_projects/images/project_state/incubating.png" width="10%">

## Comunidad

Para comunicarse con los desarrolladores, puede:  
* Unirse al [chat de Matrix](https://matrix.to/#/#apoapsis:matrix.eclipse.org).  
* Iniciar una [discusión en GitHub](https://github.com/eclipse-apoapsis/ort-server/discussions).  
* Unirse a la [lista de correo](https://accounts.eclipse.org/mailing-list/apoapsis-dev).

Por favor, informe cualquier problema en el [trackador de problemas](https://github.com/eclipse-apoapsis/ort-server/issues).

Se aceptan contribuciones; consulte la [guía de contribución](CONTRIBUTING.md) para obtener más información.

## Ejecutar el servidor ORT

La forma más sencilla de ejecutar el servidor ORT para pruebas es utilizar [Docker Compose](https://docs.docker.com/compose/).  
Para una implementación adecuada en Kubernetes, el proyecto proporcionará posteriormente un diagrama Helm.

### Docker Compose

> [!ADVERTENCIA]
> No utilice la configuración de Docker Compose en entornos de producción, ya que emplea varios valores predeterminados inseguros, como el uso de Keycloak sin TLS.

Para iniciar el servidor ORT con los servicios de terceros necesarios, ejecute:

```shell
docker compose up
```

Una vez que los servicios estén activos y en funcionamiento, podrá acceder a la interfaz de usuario del ORT Server en [http://localhost:8082](http://localhost:8082).

Consulte la [documentación](https://eclipse-apoapsis.github.io/ort-server/docs/admin-guide/getting-started/docker-compose) para obtener instrucciones detalladas, por ejemplo, sobre cómo ejecutar Docker Compose con imágenes locales.

## Publicar imágenes Docker

Para publicar las imágenes de Docker en un registro, primero construya las imágenes base del worker según se describe en la [documentación](https://eclipse-apoapsis.github.io/ort-server/docs/admin-guide/getting-started/docker-compose/#run-with-local-images). Luego puede utilizar la tarea `jib` para publicar las imágenes estableciendo el prefijo adecuado para el registro. También puede configurar la etiqueta, cuyo valor por defecto es `latest`.

```shell
# Publicar todas las imágenes Docker.
./gradlew -PdockerImagePrefix=my.registry/ tinyJibPublish

# Publicar una imagen específica.
./gradlew -PdockerImagePrefix=my.registry/ :core:tinyJibPublish

# Publicar utilizando una etiqueta personalizada.
./gradlew -PdockerImagePrefix=my.registry/ -PdockerImageTag=custom tinyJibPublish
```

## Generar especificación OpenAPI

La especificación OpenAPI se puede generar ejecutando esta tarea de Gradle:

```shell
./gradlew :core:generateOpenApiSpec
```

Esta tarea escribe la especificación en `ui/build/openapi.json`.

## Licencia

Consulte el archivo [NOTICE](./NOTICE) en la raíz de este proyecto para obtener los detalles sobre los derechos de autor.

Consulte el archivo [LICENSE](./LICENSE) en la raíz de este proyecto para obtener los detalles de la licencia.
