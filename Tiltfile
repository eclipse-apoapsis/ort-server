load('ext://helm_remote', 'helm_remote')
load('ext://helm_resource', 'helm_resource', 'helm_repo')
load('ext://configmap', 'configmap_create', 'configmap_from_dict')
load('ext://secret', 'secret_create_generic')

update_settings(k8s_upsert_timeout_secs=120)

gradlew = "./gradlew"
if os.name == "nt":
  gradlew = "gradlew.bat"

k8s_yaml('./scripts/kubernetes/namespace.yaml')

helm_resource(
  'postgresql',
  'bitnami/postgresql',
  resource_deps=['bitnami'],
  namespace='ort-server',
  flags=[
    '--set=global.postgresql.auth.postgresPassword=postgres',
    '--set=global.postgresql.auth.database=ort',
  ],
  labels=['ort-server']
)

helm_repo('bitnami', 'https://charts.bitnami.com/bitnami', labels=['helm_repos'])

configmap_create('ort-core-secrets',
  namespace='ort-server',
  from_file=['secrets.properties=./scripts/compose/secrets.properties'])

secret_create_generic('ort-secrets',
  namespace='ort-server',
  from_file=['secrets.properties=./scripts/compose/secrets.properties'])

secret_create_generic('ort-core-secrets',
  namespace='ort-server',
  from_file=['secrets.properties=./scripts/compose/secrets.properties']
  )

secret_create_generic('ort-config-secret',
  namespace='ort-server',
  from_file=[
    'evaluator.rules.kts=./scripts/compose/config/evaluator.rules.kts',
    'ort-server.params.kts=./scripts/compose/config/ort-server.params.kts',
    ]
  )
 
helm_resource(
  'keycloak',
  'bitnami/keycloak',
  resource_deps=['bitnami'],
  namespace='ort-server',
  flags=[
    '--version=21.4.5',
    '--set=auth.adminUser=keycloak-admin',
    '--set=auth.adminPassword=keycloak-admin',
    '--set=extraStartupArgs=--hostname-strict-backchannel=false',
  ],
  labels=['keycloak']
)

local_resource('keycloak-terraform',
  resource_deps=['keycloak'],
  cmd='cd ./scripts/kubernetes/keycloak && tofu init && tofu apply -auto-approve',
  deps=['./scripts/kubernetes/keycloak/keycloak.tf'],
  labels=['keycloak']
)

# Keycloak port forward has to be done manually because the Chart contains multiple containers, and
# the port forward may hit the database if done in helm_resource.
k8s_resource(
  workload='keycloak',
  port_forwards=["8081:8080"],
  extra_pod_selectors={'statefulset.kubernetes.io/pod-name': 'keycloak-0'},
  discovery_strategy='selectors-only')

helm_resource(
  'rabbitmq',
  'bitnami/rabbitmq',
  resource_deps=['bitnami'],
  namespace='ort-server',
  flags=[
    '--version=14.4.6',
    "--set=auth.username=admin",
    "--set=auth.password=admin",
  ],
  labels=['rabbitmq']
)

k8s_resource(
  workload='rabbitmq',
  port_forwards=["15672"],
)

local_resource('rabbitmq-terraform',
  resource_deps=['rabbitmq'],
  cmd='cd ./scripts/kubernetes/rabbitmq && tofu init && tofu apply -auto-approve',
  deps=['./scripts/kubernetes/rabbitmq/rabbitmq.tf'],
  labels=['rabbitmq']
)

helm_repo('kiwigrid', 'https://kiwigrid.github.io', labels=['helm_repos'])

helm_resource(
  'graphite',
  'kiwigrid/graphite',
  resource_deps=['kiwigrid'],
  namespace='ort-server',
  labels=['monitoring'],
)

custom_build(
  'core',
  './gradlew :core:jibDockerBuild --image $EXPECTED_REF',
  live_update= [
    sync('./core/build/classes/kotlin/main', '/app/classes')
  ],
  deps=['./core/build/classes', './core/build.gradle.kts',],
)

k8s_resource(
  workload='ort-server-core',
  port_forwards=[
    port_forward(8080, 8080, "API Endpoint")],
  links=[
    link('http://localhost:8080/swagger-ui', "Swagger UI"),
  ],
  resource_deps=['keycloak', 'rabbitmq', 'rabbitmq-terraform', 'graphite', 'postgresql', 'keycloak-terraform'],
  labels=['ort-server'],
)

configmap_create('ort-orchestrator-config',
  namespace='ort-server',
  from_file=['application.conf=./scripts/kubernetes/orchestrator.application.conf'])

secret_create_generic('ort-config-worker-config',
  secret_type='generic',
  namespace='ort-server',
  from_file=['application.conf=./scripts/kubernetes/config.application.conf'],
  )

k8s_resource(
  workload='ort-server-orchestrator',
  resource_deps=['keycloak', 'rabbitmq', 'rabbitmq-terraform', 'graphite', 'postgresql', 'ort-server-core', 'worker-base-images'],
  labels=['ort-server'],
)

custom_build(
  'ort-server-orchestrator',
  './gradlew :orchestrator:jibDockerBuild --image $EXPECTED_REF',
  live_update= [
    sync('./orchestrator/build/classes/kotlin/main', '/app/classes')
  ],
  deps=['./orchestrator/build/classes', './orchestrator/build.gradle.kts', './scripts/kubernetes/orchestrator.application.conf'],
)

k8s_yaml('./scripts/kubernetes/core.yaml')
k8s_yaml('./scripts/kubernetes/orchestrator.yaml')

# Worker images

local_resource(
  'worker-base-images',
  cmd='./gradlew buildAllWorkerImages',
  deps=[
    './workers/analyzer/docker/Analyzer.Dockerfile',
    './workers/config/docker/Config.Dockerfile',
    './workers/evaluator/docker/Evaluator.Dockerfile',
    './workers/notifier/docker/Notifier.Dockerfile',
    './workers/reporter/docker/Reporter.Dockerfile',
    './workers/scanner/docker/Scanner.Dockerfile'
  ],
  labels=["ort-server"]
)

custom_build(
  'advisor-worker-image',
  './gradlew :workers:advisor:jibDockerBuild --image $EXPECTED_REF',
  live_update= [
    sync('./workers/advisor/build/classes/kotlin/main', '/app/classes')
  ],
  deps=['./workers/advisor/build/classes', './workers/advisor/build.gradle.kts'],
  match_in_env_vars=True,
)

custom_build(
  'analyzer-worker-image',
  './gradlew :workers:analyzer:jibDockerBuild --image $EXPECTED_REF',
  live_update= [
    sync('./workers/analyzer/build/classes/kotlin/main', '/app/classes')
  ],
  deps=['./workers/analyzer/build/classes', './workers/analyzer/build.gradle.kts'],
  match_in_env_vars=True,
)

custom_build(
  'config-worker-image',
  './gradlew :workers:config:jibDockerBuild --image $EXPECTED_REF',
  live_update= [
    sync('./workers/config/build/classes/kotlin/main', '/app/classes')
  ],
  deps=['./workers/config/build/classes', './workers/config/build.gradle.kts'],
  match_in_env_vars=True,
)

custom_build(
  'evaluator-worker-image',
  './gradlew :workers:evaluator:jibDockerBuild --image $EXPECTED_REF',
  live_update= [
    sync('./workers/evaluator/build/classes/kotlin/main', '/app/classes')
  ],
  deps=['./workers/evaluator/build/classes', './workers/evaluator/build.gradle.kts'],
  match_in_env_vars=True,
)

custom_build(
  'notifier-worker-image',
  './gradlew :workers:notifier:jibDockerBuild --image $EXPECTED_REF',
  live_update= [
    sync('./workers/notifier/build/classes/kotlin/main', '/app/classes')
  ],
  deps=['./workers/notifier/build/classes', './workers/notifier/build.gradle.kts'],
  match_in_env_vars=True,
)

custom_build(
  'reporter-worker-image',
  './gradlew :workers:reporter:jibDockerBuild --image $EXPECTED_REF',
  live_update= [
    sync('./workers/reporter/build/classes/kotlin/main', '/app/classes')
  ],
  deps=['./workers/reporter/build/classes', './workers/reporter/build.gradle.kts'],
  match_in_env_vars=True,
)

custom_build(
  'scanner-worker-image',
  './gradlew :workers:scanner:jibDockerBuild --image $EXPECTED_REF',
  live_update= [
    sync('./workers/scanner/build/classes/kotlin/main', '/app/classes')
  ],
  deps=['./workers/scanner/build/classes', './workers/scanner/build.gradle.kts'],
  match_in_env_vars=True,
)

helm_repo('grafana-repo', 'https://grafana.github.io/helm-charts', labels=['helm_repos'])

helm_resource(
  'loki-stack',
  'grafana/loki-stack',
  resource_deps=['grafana-repo'],
  namespace='ort-server',
  flags=[
    '--values=./scripts/kubernetes/loki-values.yaml',
  ],
  deps=['./scripts/kubernetes/loki-values.yaml'],
  labels=['monitoring'],
)

k8s_yaml('./scripts/kubernetes/alloy-config.yaml')

helm_resource(
  'alloy',
  'grafana/alloy',
  resource_deps=['grafana-repo'],
  namespace='ort-server',
  flags=[
    '--values=./scripts/kubernetes/grafana-alloy-values.yaml',
  ],
  deps=['./scripts/kubernetes/grafana-alloy-values.yaml', './scripts/kubernetes/alloy-config.yaml'],
  labels=['monitoring'],
)

k8s_resource(
  workload='loki-stack',
  port_forwards=["19000:3000"],
  extra_pod_selectors={'app.kubernetes.io/name': 'grafana'},
  discovery_strategy='selectors-only')

custom_build(
  'kubernetes-jobmonitor-image',
  './gradlew :transport:kubernetes-jobmonitor:jibDockerBuild --image $EXPECTED_REF',
  live_update= [
    sync('./transport/kubernetes-jobmonitor/build/classes/kotlin/main', '/app/classes')
  ],
  deps=[
    './transport/kubernetes-jobmonitor/build/classes',
    './transport/kubernetes-jobmonitor/build.gradle.kts',
    './scripts/kubernetes/jobmonitor.application.conf'],
  match_in_env_vars=True,
)

configmap_create('ort-jobmonitor-config',
  namespace='ort-server',
  from_file=['application.conf=./scripts/kubernetes/jobmonitor.application.conf'])

k8s_yaml('./scripts/kubernetes/kubernetes-jobmonitor.yaml')

k8s_resource(
  workload='kubernetes-jobmonitor',
  resource_deps=['rabbitmq', 'rabbitmq-terraform', 'postgresql'],
  labels=['ort-server'],
)
