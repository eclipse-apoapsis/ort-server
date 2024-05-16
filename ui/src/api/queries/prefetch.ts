// generated with @7nohe/openapi-react-query-codegen@1.3.0 

import { type QueryClient } from "@tanstack/react-query";
import { HealthService, InfrastructureServicesService, LogsService, OrganizationsService, ProductsService, ReportsService, RepositoriesService, SecretsService } from "../requests/services.gen";
import * as Common from "./common";
/**
* Get the health of the ORT server.
* @returns Liveness Success
* @throws ApiError
*/
export const prefetchUseHealthServiceGetLiveness = (queryClient: QueryClient) => queryClient.prefetchQuery({ queryKey: [Common.useHealthServiceGetLivenessKey, []], queryFn: () => HealthService.getLiveness() });
/**
* Download a report of an ORT run using a token. This endpoint does not require authentication.
* @param data The data for the request.
* @param data.runId The ID of the ORT run.
* @param data.token The token providing access to the report file to be downloaded.
* @returns string Success. The response body contains the requested report file.
* @throws ApiError
*/
export const prefetchUseReportsServiceGetReportByRunIdAndToken = (queryClient: QueryClient, { runId, token }: {
  runId?: number;
  token?: string;
} = {}) => queryClient.prefetchQuery({ queryKey: [Common.useReportsServiceGetReportByRunIdAndTokenKey, [{ runId, token }]], queryFn: () => ReportsService.getReportByRunIdAndToken({ runId, token }) });
/**
* Download a report of an ORT run.
* @param data The data for the request.
* @param data.runId The ID of the ORT run.
* @param data.fileName The name of the report file to be downloaded.
* @returns string Success. The response body contains the requested report file.
* @throws ApiError
*/
export const prefetchUseReportsServiceGetReportByRunIdAndFileName = (queryClient: QueryClient, { fileName, runId }: {
  fileName?: string;
  runId?: number;
} = {}) => queryClient.prefetchQuery({ queryKey: [Common.useReportsServiceGetReportByRunIdAndFileNameKey, [{ fileName, runId }]], queryFn: () => ReportsService.getReportByRunIdAndFileName({ fileName, runId }) });
/**
* Get all organizations.
* @param data The data for the request.
* @param data.limit The maximum number of items to retrieve. If not specified at most 20 items are retrieved.
* @param data.offset The offset of the first item in the result. Together with 'limit', this can be used to implement paging.
* @param data.sort Comma-separated list of fields by which the result is sorted. The listed fields must be supported by the endpoint. Putting a minus ('-') before a field name, reverts the sort order for this field. If not specified, a default sort field and sort order is used.
* @returns org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Organization_ Success
* @throws ApiError
*/
export const prefetchUseOrganizationsServiceGetOrganizations = (queryClient: QueryClient, { limit, offset, sort }: {
  limit?: number;
  offset?: number;
  sort?: string;
} = {}) => queryClient.prefetchQuery({ queryKey: [Common.useOrganizationsServiceGetOrganizationsKey, [{ limit, offset, sort }]], queryFn: () => OrganizationsService.getOrganizations({ limit, offset, sort }) });
/**
* Get details of an organization.
* @param data The data for the request.
* @param data.organizationId The organization's ID.
* @returns Organization Success
* @throws ApiError
*/
export const prefetchUseOrganizationsServiceGetOrganizationById = (queryClient: QueryClient, { organizationId }: {
  organizationId?: number;
} = {}) => queryClient.prefetchQuery({ queryKey: [Common.useOrganizationsServiceGetOrganizationByIdKey, [{ organizationId }]], queryFn: () => OrganizationsService.getOrganizationById({ organizationId }) });
/**
* Get all products of an organization.
* @param data The data for the request.
* @param data.organizationId The organization's ID.
* @param data.limit The maximum number of items to retrieve. If not specified at most 20 items are retrieved.
* @param data.offset The offset of the first item in the result. Together with 'limit', this can be used to implement paging.
* @param data.sort Comma-separated list of fields by which the result is sorted. The listed fields must be supported by the endpoint. Putting a minus ('-') before a field name, reverts the sort order for this field. If not specified, a default sort field and sort order is used.
* @returns org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Product_ Success
* @throws ApiError
*/
export const prefetchUseProductsServiceGetOrganizationProducts = (queryClient: QueryClient, { limit, offset, organizationId, sort }: {
  limit?: number;
  offset?: number;
  organizationId?: number;
  sort?: string;
} = {}) => queryClient.prefetchQuery({ queryKey: [Common.useProductsServiceGetOrganizationProductsKey, [{ limit, offset, organizationId, sort }]], queryFn: () => ProductsService.getOrganizationProducts({ limit, offset, organizationId, sort }) });
/**
* Get details of a product.
* @param data The data for the request.
* @param data.productId The product's ID.
* @returns Product Success
* @throws ApiError
*/
export const prefetchUseProductsServiceGetProductById = (queryClient: QueryClient, { productId }: {
  productId?: number;
} = {}) => queryClient.prefetchQuery({ queryKey: [Common.useProductsServiceGetProductByIdKey, [{ productId }]], queryFn: () => ProductsService.getProductById({ productId }) });
/**
* Get all secrets of an organization.
* @param data The data for the request.
* @param data.organizationId The ID of an organization.
* @param data.limit The maximum number of items to retrieve. If not specified at most 20 items are retrieved.
* @param data.offset The offset of the first item in the result. Together with 'limit', this can be used to implement paging.
* @param data.sort Comma-separated list of fields by which the result is sorted. The listed fields must be supported by the endpoint. Putting a minus ('-') before a field name, reverts the sort order for this field. If not specified, a default sort field and sort order is used.
* @returns org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Secret_ Success
* @throws ApiError
*/
export const prefetchUseSecretsServiceGetSecretsByOrganizationId = (queryClient: QueryClient, { limit, offset, organizationId, sort }: {
  limit?: number;
  offset?: number;
  organizationId?: number;
  sort?: string;
} = {}) => queryClient.prefetchQuery({ queryKey: [Common.useSecretsServiceGetSecretsByOrganizationIdKey, [{ limit, offset, organizationId, sort }]], queryFn: () => SecretsService.getSecretsByOrganizationId({ limit, offset, organizationId, sort }) });
/**
* Get details of a secret of an organization.
* @param data The data for the request.
* @param data.organizationId The organization's ID.
* @param data.secretName The secret's name.
* @returns Secret Success
* @throws ApiError
*/
export const prefetchUseSecretsServiceGetSecretByOrganizationIdAndName = (queryClient: QueryClient, { organizationId, secretName }: {
  organizationId?: number;
  secretName?: string;
} = {}) => queryClient.prefetchQuery({ queryKey: [Common.useSecretsServiceGetSecretByOrganizationIdAndNameKey, [{ organizationId, secretName }]], queryFn: () => SecretsService.getSecretByOrganizationIdAndName({ organizationId, secretName }) });
/**
* Get all secrets of a specific product.
* @param data The data for the request.
* @param data.productId The ID of a product.
* @param data.limit The maximum number of items to retrieve. If not specified at most 20 items are retrieved.
* @param data.offset The offset of the first item in the result. Together with 'limit', this can be used to implement paging.
* @param data.sort Comma-separated list of fields by which the result is sorted. The listed fields must be supported by the endpoint. Putting a minus ('-') before a field name, reverts the sort order for this field. If not specified, a default sort field and sort order is used.
* @returns org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Secret_ Success
* @throws ApiError
*/
export const prefetchUseSecretsServiceGetSecretsByProductId = (queryClient: QueryClient, { limit, offset, productId, sort }: {
  limit?: number;
  offset?: number;
  productId?: number;
  sort?: string;
} = {}) => queryClient.prefetchQuery({ queryKey: [Common.useSecretsServiceGetSecretsByProductIdKey, [{ limit, offset, productId, sort }]], queryFn: () => SecretsService.getSecretsByProductId({ limit, offset, productId, sort }) });
/**
* Get details of a secret of a product.
* @param data The data for the request.
* @param data.productId The product's ID.
* @param data.secretName The secret's name.
* @returns Secret Success
* @throws ApiError
*/
export const prefetchUseSecretsServiceGetSecretByProductIdAndName = (queryClient: QueryClient, { productId, secretName }: {
  productId?: number;
  secretName?: string;
} = {}) => queryClient.prefetchQuery({ queryKey: [Common.useSecretsServiceGetSecretByProductIdAndNameKey, [{ productId, secretName }]], queryFn: () => SecretsService.getSecretByProductIdAndName({ productId, secretName }) });
/**
* Get all secrets of a repository.
* @param data The data for the request.
* @param data.repositoryId The ID of a repository.
* @param data.limit The maximum number of items to retrieve. If not specified at most 20 items are retrieved.
* @param data.offset The offset of the first item in the result. Together with 'limit', this can be used to implement paging.
* @param data.sort Comma-separated list of fields by which the result is sorted. The listed fields must be supported by the endpoint. Putting a minus ('-') before a field name, reverts the sort order for this field. If not specified, a default sort field and sort order is used.
* @returns org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Secret_ Success
* @throws ApiError
*/
export const prefetchUseSecretsServiceGetSecretsByRepositoryId = (queryClient: QueryClient, { limit, offset, repositoryId, sort }: {
  limit?: number;
  offset?: number;
  repositoryId?: number;
  sort?: string;
} = {}) => queryClient.prefetchQuery({ queryKey: [Common.useSecretsServiceGetSecretsByRepositoryIdKey, [{ limit, offset, repositoryId, sort }]], queryFn: () => SecretsService.getSecretsByRepositoryId({ limit, offset, repositoryId, sort }) });
/**
* Get details of a secret of a repository.
* @param data The data for the request.
* @param data.repositoryId The repository's ID.
* @param data.secretName The secret's name.
* @returns Secret Success
* @throws ApiError
*/
export const prefetchUseSecretsServiceGetSecretByRepositoryIdAndName = (queryClient: QueryClient, { repositoryId, secretName }: {
  repositoryId?: number;
  secretName?: string;
} = {}) => queryClient.prefetchQuery({ queryKey: [Common.useSecretsServiceGetSecretByRepositoryIdAndNameKey, [{ repositoryId, secretName }]], queryFn: () => SecretsService.getSecretByRepositoryIdAndName({ repositoryId, secretName }) });
/**
* List all infrastructure services of an organization.
* @param data The data for the request.
* @param data.organizationId The ID of an organization.
* @param data.limit The maximum number of items to retrieve. If not specified at most 20 items are retrieved.
* @param data.offset The offset of the first item in the result. Together with 'limit', this can be used to implement paging.
* @param data.sort Comma-separated list of fields by which the result is sorted. The listed fields must be supported by the endpoint. Putting a minus ('-') before a field name, reverts the sort order for this field. If not specified, a default sort field and sort order is used.
* @returns org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_InfrastructureService_ Success
* @throws ApiError
*/
export const prefetchUseInfrastructureServicesServiceGetInfrastructureServicesByOrganizationId = (queryClient: QueryClient, { limit, offset, organizationId, sort }: {
  limit?: number;
  offset?: number;
  organizationId?: number;
  sort?: string;
} = {}) => queryClient.prefetchQuery({ queryKey: [Common.useInfrastructureServicesServiceGetInfrastructureServicesByOrganizationIdKey, [{ limit, offset, organizationId, sort }]], queryFn: () => InfrastructureServicesService.getInfrastructureServicesByOrganizationId({ limit, offset, organizationId, sort }) });
/**
* Get all repositories of a product.
* @param data The data for the request.
* @param data.productId The product's ID.
* @param data.limit The maximum number of items to retrieve. If not specified at most 20 items are retrieved.
* @param data.offset The offset of the first item in the result. Together with 'limit', this can be used to implement paging.
* @param data.sort Comma-separated list of fields by which the result is sorted. The listed fields must be supported by the endpoint. Putting a minus ('-') before a field name, reverts the sort order for this field. If not specified, a default sort field and sort order is used.
* @returns org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Repository_ Success
* @throws ApiError
*/
export const prefetchUseRepositoriesServiceGetRepositoriesByProductId = (queryClient: QueryClient, { limit, offset, productId, sort }: {
  limit?: number;
  offset?: number;
  productId?: number;
  sort?: string;
} = {}) => queryClient.prefetchQuery({ queryKey: [Common.useRepositoriesServiceGetRepositoriesByProductIdKey, [{ limit, offset, productId, sort }]], queryFn: () => RepositoriesService.getRepositoriesByProductId({ limit, offset, productId, sort }) });
/**
* Get details of a repository.
* @param data The data for the request.
* @param data.repositoryId The repository's ID.
* @returns Repository Success
* @throws ApiError
*/
export const prefetchUseRepositoriesServiceGetRepositoryById = (queryClient: QueryClient, { repositoryId }: {
  repositoryId?: number;
} = {}) => queryClient.prefetchQuery({ queryKey: [Common.useRepositoriesServiceGetRepositoryByIdKey, [{ repositoryId }]], queryFn: () => RepositoriesService.getRepositoryById({ repositoryId }) });
/**
* Get all ORT runs of a repository.
* @param data The data for the request.
* @param data.repositoryId The repository's ID.
* @param data.limit The maximum number of items to retrieve. If not specified at most 20 items are retrieved.
* @param data.offset The offset of the first item in the result. Together with 'limit', this can be used to implement paging.
* @param data.sort Comma-separated list of fields by which the result is sorted. The listed fields must be supported by the endpoint. Putting a minus ('-') before a field name, reverts the sort order for this field. If not specified, a default sort field and sort order is used.
* @returns org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_OrtRunSummary_ Success
* @throws ApiError
*/
export const prefetchUseRepositoriesServiceGetOrtRuns = (queryClient: QueryClient, { limit, offset, repositoryId, sort }: {
  limit?: number;
  offset?: number;
  repositoryId?: number;
  sort?: string;
} = {}) => queryClient.prefetchQuery({ queryKey: [Common.useRepositoriesServiceGetOrtRunsKey, [{ limit, offset, repositoryId, sort }]], queryFn: () => RepositoriesService.getOrtRuns({ limit, offset, repositoryId, sort }) });
/**
* Get details of an ORT run of a repository.
* @param data The data for the request.
* @param data.repositoryId The repository's ID.
* @param data.ortRunIndex The index of an ORT run.
* @returns OrtRun Success
* @throws ApiError
*/
export const prefetchUseRepositoriesServiceGetOrtRunByIndex = (queryClient: QueryClient, { ortRunIndex, repositoryId }: {
  ortRunIndex?: number;
  repositoryId?: number;
} = {}) => queryClient.prefetchQuery({ queryKey: [Common.useRepositoriesServiceGetOrtRunByIndexKey, [{ ortRunIndex, repositoryId }]], queryFn: () => RepositoriesService.getOrtRunByIndex({ ortRunIndex, repositoryId }) });
/**
* Download an archive with selected logs of an ORT run.
* @param data The data for the request.
* @param data.runId The ID of the ORT run.
* @param data.level The log level; can be one of 'DEBUG', 'INFO', 'WARN', 'ERROR' (ignoring case).Only logs of this level or higher are retrieved. Defaults to 'INFO' if missing.
* @param data.steps Defines the run steps for which logs are to be retrieved. This is a comma-separated string with the following allowed steps: 'CONFIG', 'ANALYZER', 'ADVISOR', 'SCANNER', 'EVALUATOR', 'REPORTER' (ignoring case). If missing, the logs for all steps are retrieved.
* @returns unknown Success. The response body contains a Zip archive with the selected log files.
* @throws ApiError
*/
export const prefetchUseLogsServiceGetLogsByRunId = (queryClient: QueryClient, { level, runId, steps }: {
  level?: string;
  runId?: number;
  steps?: string;
} = {}) => queryClient.prefetchQuery({ queryKey: [Common.useLogsServiceGetLogsByRunIdKey, [{ level, runId, steps }]], queryFn: () => LogsService.getLogsByRunId({ level, runId, steps }) });
