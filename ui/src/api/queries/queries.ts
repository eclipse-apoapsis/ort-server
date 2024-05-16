// generated with @7nohe/openapi-react-query-codegen@1.3.0 

import { UseMutationOptions, UseQueryOptions, useMutation, useQuery } from "@tanstack/react-query";
import { HealthService, InfrastructureServicesService, LogsService, OrganizationsService, ProductsService, ReportsService, RepositoriesService, SecretsService } from "../requests/services.gen";
import { CreateInfrastructureService, CreateOrganization, CreateOrtRun, CreateProduct, CreateRepository, CreateSecret, UpdateInfrastructureService, UpdateOrganization, UpdateProduct, UpdateRepository, UpdateSecret } from "../requests/types.gen";
import * as Common from "./common";
/**
* Get the health of the ORT server.
* @returns Liveness Success
* @throws ApiError
*/
export const useHealthServiceGetLiveness = <TData = Common.HealthServiceGetLivenessDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>(queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseHealthServiceGetLivenessKeyFn(), queryFn: () => HealthService.getLiveness() as TData, ...options });
/**
* Download a report of an ORT run using a token. This endpoint does not require authentication.
* @param data The data for the request.
* @param data.runId The ID of the ORT run.
* @param data.token The token providing access to the report file to be downloaded.
* @returns string Success. The response body contains the requested report file.
* @throws ApiError
*/
export const useReportsServiceGetReportByRunIdAndToken = <TData = Common.ReportsServiceGetReportByRunIdAndTokenDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>({ runId, token }: {
  runId?: number;
  token?: string;
} = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseReportsServiceGetReportByRunIdAndTokenKeyFn({ runId, token }, queryKey), queryFn: () => ReportsService.getReportByRunIdAndToken({ runId, token }) as TData, ...options });
/**
* Download a report of an ORT run.
* @param data The data for the request.
* @param data.runId The ID of the ORT run.
* @param data.fileName The name of the report file to be downloaded.
* @returns string Success. The response body contains the requested report file.
* @throws ApiError
*/
export const useReportsServiceGetReportByRunIdAndFileName = <TData = Common.ReportsServiceGetReportByRunIdAndFileNameDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>({ fileName, runId }: {
  fileName?: string;
  runId?: number;
} = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseReportsServiceGetReportByRunIdAndFileNameKeyFn({ fileName, runId }, queryKey), queryFn: () => ReportsService.getReportByRunIdAndFileName({ fileName, runId }) as TData, ...options });
/**
* Get all organizations.
* @param data The data for the request.
* @param data.limit The maximum number of items to retrieve. If not specified at most 20 items are retrieved.
* @param data.offset The offset of the first item in the result. Together with 'limit', this can be used to implement paging.
* @param data.sort Comma-separated list of fields by which the result is sorted. The listed fields must be supported by the endpoint. Putting a minus ('-') before a field name, reverts the sort order for this field. If not specified, a default sort field and sort order is used.
* @returns org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Organization_ Success
* @throws ApiError
*/
export const useOrganizationsServiceGetOrganizations = <TData = Common.OrganizationsServiceGetOrganizationsDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>({ limit, offset, sort }: {
  limit?: number;
  offset?: number;
  sort?: string;
} = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseOrganizationsServiceGetOrganizationsKeyFn({ limit, offset, sort }, queryKey), queryFn: () => OrganizationsService.getOrganizations({ limit, offset, sort }) as TData, ...options });
/**
* Get details of an organization.
* @param data The data for the request.
* @param data.organizationId The organization's ID.
* @returns Organization Success
* @throws ApiError
*/
export const useOrganizationsServiceGetOrganizationById = <TData = Common.OrganizationsServiceGetOrganizationByIdDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>({ organizationId }: {
  organizationId?: number;
} = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseOrganizationsServiceGetOrganizationByIdKeyFn({ organizationId }, queryKey), queryFn: () => OrganizationsService.getOrganizationById({ organizationId }) as TData, ...options });
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
export const useProductsServiceGetOrganizationProducts = <TData = Common.ProductsServiceGetOrganizationProductsDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>({ limit, offset, organizationId, sort }: {
  limit?: number;
  offset?: number;
  organizationId?: number;
  sort?: string;
} = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseProductsServiceGetOrganizationProductsKeyFn({ limit, offset, organizationId, sort }, queryKey), queryFn: () => ProductsService.getOrganizationProducts({ limit, offset, organizationId, sort }) as TData, ...options });
/**
* Get details of a product.
* @param data The data for the request.
* @param data.productId The product's ID.
* @returns Product Success
* @throws ApiError
*/
export const useProductsServiceGetProductById = <TData = Common.ProductsServiceGetProductByIdDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>({ productId }: {
  productId?: number;
} = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseProductsServiceGetProductByIdKeyFn({ productId }, queryKey), queryFn: () => ProductsService.getProductById({ productId }) as TData, ...options });
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
export const useSecretsServiceGetSecretsByOrganizationId = <TData = Common.SecretsServiceGetSecretsByOrganizationIdDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>({ limit, offset, organizationId, sort }: {
  limit?: number;
  offset?: number;
  organizationId?: number;
  sort?: string;
} = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseSecretsServiceGetSecretsByOrganizationIdKeyFn({ limit, offset, organizationId, sort }, queryKey), queryFn: () => SecretsService.getSecretsByOrganizationId({ limit, offset, organizationId, sort }) as TData, ...options });
/**
* Get details of a secret of an organization.
* @param data The data for the request.
* @param data.organizationId The organization's ID.
* @param data.secretName The secret's name.
* @returns Secret Success
* @throws ApiError
*/
export const useSecretsServiceGetSecretByOrganizationIdAndName = <TData = Common.SecretsServiceGetSecretByOrganizationIdAndNameDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>({ organizationId, secretName }: {
  organizationId?: number;
  secretName?: string;
} = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseSecretsServiceGetSecretByOrganizationIdAndNameKeyFn({ organizationId, secretName }, queryKey), queryFn: () => SecretsService.getSecretByOrganizationIdAndName({ organizationId, secretName }) as TData, ...options });
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
export const useSecretsServiceGetSecretsByProductId = <TData = Common.SecretsServiceGetSecretsByProductIdDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>({ limit, offset, productId, sort }: {
  limit?: number;
  offset?: number;
  productId?: number;
  sort?: string;
} = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseSecretsServiceGetSecretsByProductIdKeyFn({ limit, offset, productId, sort }, queryKey), queryFn: () => SecretsService.getSecretsByProductId({ limit, offset, productId, sort }) as TData, ...options });
/**
* Get details of a secret of a product.
* @param data The data for the request.
* @param data.productId The product's ID.
* @param data.secretName The secret's name.
* @returns Secret Success
* @throws ApiError
*/
export const useSecretsServiceGetSecretByProductIdAndName = <TData = Common.SecretsServiceGetSecretByProductIdAndNameDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>({ productId, secretName }: {
  productId?: number;
  secretName?: string;
} = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseSecretsServiceGetSecretByProductIdAndNameKeyFn({ productId, secretName }, queryKey), queryFn: () => SecretsService.getSecretByProductIdAndName({ productId, secretName }) as TData, ...options });
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
export const useSecretsServiceGetSecretsByRepositoryId = <TData = Common.SecretsServiceGetSecretsByRepositoryIdDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>({ limit, offset, repositoryId, sort }: {
  limit?: number;
  offset?: number;
  repositoryId?: number;
  sort?: string;
} = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseSecretsServiceGetSecretsByRepositoryIdKeyFn({ limit, offset, repositoryId, sort }, queryKey), queryFn: () => SecretsService.getSecretsByRepositoryId({ limit, offset, repositoryId, sort }) as TData, ...options });
/**
* Get details of a secret of a repository.
* @param data The data for the request.
* @param data.repositoryId The repository's ID.
* @param data.secretName The secret's name.
* @returns Secret Success
* @throws ApiError
*/
export const useSecretsServiceGetSecretByRepositoryIdAndName = <TData = Common.SecretsServiceGetSecretByRepositoryIdAndNameDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>({ repositoryId, secretName }: {
  repositoryId?: number;
  secretName?: string;
} = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseSecretsServiceGetSecretByRepositoryIdAndNameKeyFn({ repositoryId, secretName }, queryKey), queryFn: () => SecretsService.getSecretByRepositoryIdAndName({ repositoryId, secretName }) as TData, ...options });
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
export const useInfrastructureServicesServiceGetInfrastructureServicesByOrganizationId = <TData = Common.InfrastructureServicesServiceGetInfrastructureServicesByOrganizationIdDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>({ limit, offset, organizationId, sort }: {
  limit?: number;
  offset?: number;
  organizationId?: number;
  sort?: string;
} = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseInfrastructureServicesServiceGetInfrastructureServicesByOrganizationIdKeyFn({ limit, offset, organizationId, sort }, queryKey), queryFn: () => InfrastructureServicesService.getInfrastructureServicesByOrganizationId({ limit, offset, organizationId, sort }) as TData, ...options });
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
export const useRepositoriesServiceGetRepositoriesByProductId = <TData = Common.RepositoriesServiceGetRepositoriesByProductIdDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>({ limit, offset, productId, sort }: {
  limit?: number;
  offset?: number;
  productId?: number;
  sort?: string;
} = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseRepositoriesServiceGetRepositoriesByProductIdKeyFn({ limit, offset, productId, sort }, queryKey), queryFn: () => RepositoriesService.getRepositoriesByProductId({ limit, offset, productId, sort }) as TData, ...options });
/**
* Get details of a repository.
* @param data The data for the request.
* @param data.repositoryId The repository's ID.
* @returns Repository Success
* @throws ApiError
*/
export const useRepositoriesServiceGetRepositoryById = <TData = Common.RepositoriesServiceGetRepositoryByIdDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>({ repositoryId }: {
  repositoryId?: number;
} = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseRepositoriesServiceGetRepositoryByIdKeyFn({ repositoryId }, queryKey), queryFn: () => RepositoriesService.getRepositoryById({ repositoryId }) as TData, ...options });
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
export const useRepositoriesServiceGetOrtRuns = <TData = Common.RepositoriesServiceGetOrtRunsDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>({ limit, offset, repositoryId, sort }: {
  limit?: number;
  offset?: number;
  repositoryId?: number;
  sort?: string;
} = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseRepositoriesServiceGetOrtRunsKeyFn({ limit, offset, repositoryId, sort }, queryKey), queryFn: () => RepositoriesService.getOrtRuns({ limit, offset, repositoryId, sort }) as TData, ...options });
/**
* Get details of an ORT run of a repository.
* @param data The data for the request.
* @param data.repositoryId The repository's ID.
* @param data.ortRunIndex The index of an ORT run.
* @returns OrtRun Success
* @throws ApiError
*/
export const useRepositoriesServiceGetOrtRunByIndex = <TData = Common.RepositoriesServiceGetOrtRunByIndexDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>({ ortRunIndex, repositoryId }: {
  ortRunIndex?: number;
  repositoryId?: number;
} = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseRepositoriesServiceGetOrtRunByIndexKeyFn({ ortRunIndex, repositoryId }, queryKey), queryFn: () => RepositoriesService.getOrtRunByIndex({ ortRunIndex, repositoryId }) as TData, ...options });
/**
* Download an archive with selected logs of an ORT run.
* @param data The data for the request.
* @param data.runId The ID of the ORT run.
* @param data.level The log level; can be one of 'DEBUG', 'INFO', 'WARN', 'ERROR' (ignoring case).Only logs of this level or higher are retrieved. Defaults to 'INFO' if missing.
* @param data.steps Defines the run steps for which logs are to be retrieved. This is a comma-separated string with the following allowed steps: 'CONFIG', 'ANALYZER', 'ADVISOR', 'SCANNER', 'EVALUATOR', 'REPORTER' (ignoring case). If missing, the logs for all steps are retrieved.
* @returns unknown Success. The response body contains a Zip archive with the selected log files.
* @throws ApiError
*/
export const useLogsServiceGetLogsByRunId = <TData = Common.LogsServiceGetLogsByRunIdDefaultResponse, TError = unknown, TQueryKey extends Array<unknown> = unknown[]>({ level, runId, steps }: {
  level?: string;
  runId?: number;
  steps?: string;
} = {}, queryKey?: TQueryKey, options?: Omit<UseQueryOptions<TData, TError>, "queryKey" | "queryFn">) => useQuery<TData, TError>({ queryKey: Common.UseLogsServiceGetLogsByRunIdKeyFn({ level, runId, steps }, queryKey), queryFn: () => LogsService.getLogsByRunId({ level, runId, steps }) as TData, ...options });
/**
* Create an organization.
* @param data The data for the request.
* @param data.requestBody
* @returns Organization Success
* @throws ApiError
*/
export const useOrganizationsServicePostOrganizations = <TData = Common.OrganizationsServicePostOrganizationsMutationResult, TError = unknown, TContext = unknown>(options?: Omit<UseMutationOptions<TData, TError, {
  requestBody?: CreateOrganization;
}, TContext>, "mutationFn">) => useMutation<TData, TError, {
  requestBody?: CreateOrganization;
}, TContext>({ mutationFn: ({ requestBody }) => OrganizationsService.postOrganizations({ requestBody }) as unknown as Promise<TData>, ...options });
/**
* Create a product for an organization.
* @param data The data for the request.
* @param data.organizationId The organization's ID.
* @param data.requestBody
* @returns Product Success
* @throws ApiError
*/
export const useProductsServicePostProduct = <TData = Common.ProductsServicePostProductMutationResult, TError = unknown, TContext = unknown>(options?: Omit<UseMutationOptions<TData, TError, {
  organizationId?: number;
  requestBody?: CreateProduct;
}, TContext>, "mutationFn">) => useMutation<TData, TError, {
  organizationId?: number;
  requestBody?: CreateProduct;
}, TContext>({ mutationFn: ({ organizationId, requestBody }) => ProductsService.postProduct({ organizationId, requestBody }) as unknown as Promise<TData>, ...options });
/**
* Create a secret for an organization.
* @param data The data for the request.
* @param data.requestBody
* @returns Secret Success
* @throws ApiError
*/
export const useSecretsServicePostSecretForOrganization = <TData = Common.SecretsServicePostSecretForOrganizationMutationResult, TError = unknown, TContext = unknown>(options?: Omit<UseMutationOptions<TData, TError, {
  requestBody?: CreateSecret;
}, TContext>, "mutationFn">) => useMutation<TData, TError, {
  requestBody?: CreateSecret;
}, TContext>({ mutationFn: ({ requestBody }) => SecretsService.postSecretForOrganization({ requestBody }) as unknown as Promise<TData>, ...options });
/**
* Create a secret for a product.
* @param data The data for the request.
* @param data.requestBody
* @returns Secret Success
* @throws ApiError
*/
export const useSecretsServicePostSecretForProduct = <TData = Common.SecretsServicePostSecretForProductMutationResult, TError = unknown, TContext = unknown>(options?: Omit<UseMutationOptions<TData, TError, {
  requestBody?: CreateSecret;
}, TContext>, "mutationFn">) => useMutation<TData, TError, {
  requestBody?: CreateSecret;
}, TContext>({ mutationFn: ({ requestBody }) => SecretsService.postSecretForProduct({ requestBody }) as unknown as Promise<TData>, ...options });
/**
* Create a secret for a repository.
* @param data The data for the request.
* @param data.requestBody
* @returns Secret Success
* @throws ApiError
*/
export const useSecretsServicePostSecretForRepository = <TData = Common.SecretsServicePostSecretForRepositoryMutationResult, TError = unknown, TContext = unknown>(options?: Omit<UseMutationOptions<TData, TError, {
  requestBody?: CreateSecret;
}, TContext>, "mutationFn">) => useMutation<TData, TError, {
  requestBody?: CreateSecret;
}, TContext>({ mutationFn: ({ requestBody }) => SecretsService.postSecretForRepository({ requestBody }) as unknown as Promise<TData>, ...options });
/**
* Create an infrastructure service for an organization.
* @param data The data for the request.
* @param data.requestBody
* @returns InfrastructureService Success
* @throws ApiError
*/
export const useInfrastructureServicesServicePostInfrastructureServiceForOrganization = <TData = Common.InfrastructureServicesServicePostInfrastructureServiceForOrganizationMutationResult, TError = unknown, TContext = unknown>(options?: Omit<UseMutationOptions<TData, TError, {
  requestBody?: CreateInfrastructureService;
}, TContext>, "mutationFn">) => useMutation<TData, TError, {
  requestBody?: CreateInfrastructureService;
}, TContext>({ mutationFn: ({ requestBody }) => InfrastructureServicesService.postInfrastructureServiceForOrganization({ requestBody }) as unknown as Promise<TData>, ...options });
/**
* Create a repository for a product.
* @param data The data for the request.
* @param data.productId The product's ID.
* @param data.requestBody
* @returns Repository Success
* @throws ApiError
*/
export const useRepositoriesServiceCreateRepository = <TData = Common.RepositoriesServiceCreateRepositoryMutationResult, TError = unknown, TContext = unknown>(options?: Omit<UseMutationOptions<TData, TError, {
  productId?: number;
  requestBody?: CreateRepository;
}, TContext>, "mutationFn">) => useMutation<TData, TError, {
  productId?: number;
  requestBody?: CreateRepository;
}, TContext>({ mutationFn: ({ productId, requestBody }) => RepositoriesService.createRepository({ productId, requestBody }) as unknown as Promise<TData>, ...options });
/**
* Create an ORT run for a repository.
* @param data The data for the request.
* @param data.repositoryId The repository's ID.
* @param data.requestBody
* @returns OrtRun Success
* @throws ApiError
*/
export const useRepositoriesServicePostOrtRun = <TData = Common.RepositoriesServicePostOrtRunMutationResult, TError = unknown, TContext = unknown>(options?: Omit<UseMutationOptions<TData, TError, {
  repositoryId?: number;
  requestBody?: CreateOrtRun;
}, TContext>, "mutationFn">) => useMutation<TData, TError, {
  repositoryId?: number;
  requestBody?: CreateOrtRun;
}, TContext>({ mutationFn: ({ repositoryId, requestBody }) => RepositoriesService.postOrtRun({ repositoryId, requestBody }) as unknown as Promise<TData>, ...options });
/**
* Update an organization.
* @param data The data for the request.
* @param data.organizationId The organization's ID.
* @param data.requestBody Set the values that should be updated. To delete a value, set it explicitly to null.
* @returns Organization Success
* @throws ApiError
*/
export const useOrganizationsServicePatchOrganizationById = <TData = Common.OrganizationsServicePatchOrganizationByIdMutationResult, TError = unknown, TContext = unknown>(options?: Omit<UseMutationOptions<TData, TError, {
  organizationId?: number;
  requestBody?: UpdateOrganization;
}, TContext>, "mutationFn">) => useMutation<TData, TError, {
  organizationId?: number;
  requestBody?: UpdateOrganization;
}, TContext>({ mutationFn: ({ organizationId, requestBody }) => OrganizationsService.patchOrganizationById({ organizationId, requestBody }) as unknown as Promise<TData>, ...options });
/**
* Update a product.
* @param data The data for the request.
* @param data.productId The product's ID.
* @param data.requestBody Set the values that should be updated. To delete a value, set it explicitly to null.
* @returns Product Success
* @throws ApiError
*/
export const useProductsServicePatchProductById = <TData = Common.ProductsServicePatchProductByIdMutationResult, TError = unknown, TContext = unknown>(options?: Omit<UseMutationOptions<TData, TError, {
  productId?: number;
  requestBody?: UpdateProduct;
}, TContext>, "mutationFn">) => useMutation<TData, TError, {
  productId?: number;
  requestBody?: UpdateProduct;
}, TContext>({ mutationFn: ({ productId, requestBody }) => ProductsService.patchProductById({ productId, requestBody }) as unknown as Promise<TData>, ...options });
/**
* Update a secret of an organization.
* @param data The data for the request.
* @param data.organizationId The organization's ID.
* @param data.secretName The secret's name.
* @param data.requestBody Set the values that should be updated. To delete a value, set it explicitly to null.
* @returns Secret Success
* @throws ApiError
*/
export const useSecretsServicePatchSecretByOrganizationIdAndName = <TData = Common.SecretsServicePatchSecretByOrganizationIdAndNameMutationResult, TError = unknown, TContext = unknown>(options?: Omit<UseMutationOptions<TData, TError, {
  organizationId?: number;
  requestBody?: UpdateSecret;
  secretName?: string;
}, TContext>, "mutationFn">) => useMutation<TData, TError, {
  organizationId?: number;
  requestBody?: UpdateSecret;
  secretName?: string;
}, TContext>({ mutationFn: ({ organizationId, requestBody, secretName }) => SecretsService.patchSecretByOrganizationIdAndName({ organizationId, requestBody, secretName }) as unknown as Promise<TData>, ...options });
/**
* Update a secret of a product.
* @param data The data for the request.
* @param data.productId The product's ID.
* @param data.secretName The secret's name.
* @param data.requestBody Set the values that should be updated. To delete a value, set it explicitly to null.
* @returns Secret Success
* @throws ApiError
*/
export const useSecretsServicePatchSecretByProductIdIdAndName = <TData = Common.SecretsServicePatchSecretByProductIdIdAndNameMutationResult, TError = unknown, TContext = unknown>(options?: Omit<UseMutationOptions<TData, TError, {
  productId?: number;
  requestBody?: UpdateSecret;
  secretName?: string;
}, TContext>, "mutationFn">) => useMutation<TData, TError, {
  productId?: number;
  requestBody?: UpdateSecret;
  secretName?: string;
}, TContext>({ mutationFn: ({ productId, requestBody, secretName }) => SecretsService.patchSecretByProductIdIdAndName({ productId, requestBody, secretName }) as unknown as Promise<TData>, ...options });
/**
* Update a secret of a repository.
* @param data The data for the request.
* @param data.repositoryIdId The repository's ID.
* @param data.secretName The secret's name.
* @param data.requestBody Set the values that should be updated. To delete a value, set it explicitly to null.
* @returns Secret Success
* @throws ApiError
*/
export const useSecretsServicePatchSecretByRepositoryIdIdAndName = <TData = Common.SecretsServicePatchSecretByRepositoryIdIdAndNameMutationResult, TError = unknown, TContext = unknown>(options?: Omit<UseMutationOptions<TData, TError, {
  repositoryIdId?: number;
  requestBody?: UpdateSecret;
  secretName?: string;
}, TContext>, "mutationFn">) => useMutation<TData, TError, {
  repositoryIdId?: number;
  requestBody?: UpdateSecret;
  secretName?: string;
}, TContext>({ mutationFn: ({ repositoryIdId, requestBody, secretName }) => SecretsService.patchSecretByRepositoryIdIdAndName({ repositoryIdId, requestBody, secretName }) as unknown as Promise<TData>, ...options });
/**
* Update an infrastructure service for an organization.
* @param data The data for the request.
* @param data.organizationId The organization's ID.
* @param data.serviceName The name of the infrastructure service.
* @param data.requestBody Set the values that should be updated. To delete a value, set it explicitly to null.
* @returns InfrastructureService Success
* @throws ApiError
*/
export const useInfrastructureServicesServicePatchInfrastructureServiceForOrganizationIdAndName = <TData = Common.InfrastructureServicesServicePatchInfrastructureServiceForOrganizationIdAndNameMutationResult, TError = unknown, TContext = unknown>(options?: Omit<UseMutationOptions<TData, TError, {
  organizationId?: number;
  requestBody?: UpdateInfrastructureService;
  serviceName?: string;
}, TContext>, "mutationFn">) => useMutation<TData, TError, {
  organizationId?: number;
  requestBody?: UpdateInfrastructureService;
  serviceName?: string;
}, TContext>({ mutationFn: ({ organizationId, requestBody, serviceName }) => InfrastructureServicesService.patchInfrastructureServiceForOrganizationIdAndName({ organizationId, requestBody, serviceName }) as unknown as Promise<TData>, ...options });
/**
* Update a repository.
* @param data The data for the request.
* @param data.repositoryId The repository's ID.
* @param data.requestBody Set the values that should be updated. To delete a value, set it explicitly to null.
* @returns Repository Success
* @throws ApiError
*/
export const useRepositoriesServicePatchRepositoryById = <TData = Common.RepositoriesServicePatchRepositoryByIdMutationResult, TError = unknown, TContext = unknown>(options?: Omit<UseMutationOptions<TData, TError, {
  repositoryId?: number;
  requestBody?: UpdateRepository;
}, TContext>, "mutationFn">) => useMutation<TData, TError, {
  repositoryId?: number;
  requestBody?: UpdateRepository;
}, TContext>({ mutationFn: ({ repositoryId, requestBody }) => RepositoriesService.patchRepositoryById({ repositoryId, requestBody }) as unknown as Promise<TData>, ...options });
/**
* Delete an organization.
* @param data The data for the request.
* @param data.organizationId The organization's ID.
* @returns void Success
* @throws ApiError
*/
export const useOrganizationsServiceDeleteOrganizationById = <TData = Common.OrganizationsServiceDeleteOrganizationByIdMutationResult, TError = unknown, TContext = unknown>(options?: Omit<UseMutationOptions<TData, TError, {
  organizationId?: number;
}, TContext>, "mutationFn">) => useMutation<TData, TError, {
  organizationId?: number;
}, TContext>({ mutationFn: ({ organizationId }) => OrganizationsService.deleteOrganizationById({ organizationId }) as unknown as Promise<TData>, ...options });
/**
* Delete a product.
* @param data The data for the request.
* @param data.productId The product's ID.
* @returns void Success
* @throws ApiError
*/
export const useProductsServiceDeleteProductById = <TData = Common.ProductsServiceDeleteProductByIdMutationResult, TError = unknown, TContext = unknown>(options?: Omit<UseMutationOptions<TData, TError, {
  productId?: number;
}, TContext>, "mutationFn">) => useMutation<TData, TError, {
  productId?: number;
}, TContext>({ mutationFn: ({ productId }) => ProductsService.deleteProductById({ productId }) as unknown as Promise<TData>, ...options });
/**
* Delete a secret from an organization.
* @param data The data for the request.
* @param data.organizationId The organization's ID.
* @param data.secretName The secret's name.
* @returns void Success
* @throws ApiError
*/
export const useSecretsServiceDeleteSecretByOrganizationIdAndName = <TData = Common.SecretsServiceDeleteSecretByOrganizationIdAndNameMutationResult, TError = unknown, TContext = unknown>(options?: Omit<UseMutationOptions<TData, TError, {
  organizationId?: number;
  secretName?: string;
}, TContext>, "mutationFn">) => useMutation<TData, TError, {
  organizationId?: number;
  secretName?: string;
}, TContext>({ mutationFn: ({ organizationId, secretName }) => SecretsService.deleteSecretByOrganizationIdAndName({ organizationId, secretName }) as unknown as Promise<TData>, ...options });
/**
* Delete a secret from a product.
* @param data The data for the request.
* @param data.productId The product's ID.
* @param data.secretName The secret's name.
* @returns void Success
* @throws ApiError
*/
export const useSecretsServiceDeleteSecretByProductIdAndName = <TData = Common.SecretsServiceDeleteSecretByProductIdAndNameMutationResult, TError = unknown, TContext = unknown>(options?: Omit<UseMutationOptions<TData, TError, {
  productId?: number;
  secretName?: string;
}, TContext>, "mutationFn">) => useMutation<TData, TError, {
  productId?: number;
  secretName?: string;
}, TContext>({ mutationFn: ({ productId, secretName }) => SecretsService.deleteSecretByProductIdAndName({ productId, secretName }) as unknown as Promise<TData>, ...options });
/**
* Delete a secret from a repository.
* @param data The data for the request.
* @param data.repositoryId The repository's ID.
* @param data.secretName The secret's name.
* @returns void Success
* @throws ApiError
*/
export const useSecretsServiceDeleteSecretByRepositoryIdAndName = <TData = Common.SecretsServiceDeleteSecretByRepositoryIdAndNameMutationResult, TError = unknown, TContext = unknown>(options?: Omit<UseMutationOptions<TData, TError, {
  repositoryId?: number;
  secretName?: string;
}, TContext>, "mutationFn">) => useMutation<TData, TError, {
  repositoryId?: number;
  secretName?: string;
}, TContext>({ mutationFn: ({ repositoryId, secretName }) => SecretsService.deleteSecretByRepositoryIdAndName({ repositoryId, secretName }) as unknown as Promise<TData>, ...options });
/**
* Delete an infrastructure service from an organization.
* @param data The data for the request.
* @param data.organizationId The organization's ID.
* @param data.serviceName The name of the infrastructure service.
* @returns void Success
* @throws ApiError
*/
export const useInfrastructureServicesServiceDeleteInfrastructureServiceForOrganizationIdAndName = <TData = Common.InfrastructureServicesServiceDeleteInfrastructureServiceForOrganizationIdAndNameMutationResult, TError = unknown, TContext = unknown>(options?: Omit<UseMutationOptions<TData, TError, {
  organizationId?: number;
  serviceName?: string;
}, TContext>, "mutationFn">) => useMutation<TData, TError, {
  organizationId?: number;
  serviceName?: string;
}, TContext>({ mutationFn: ({ organizationId, serviceName }) => InfrastructureServicesService.deleteInfrastructureServiceForOrganizationIdAndName({ organizationId, serviceName }) as unknown as Promise<TData>, ...options });
/**
* Delete a repository.
* @param data The data for the request.
* @param data.repositoryId The repository's ID.
* @returns void Success
* @throws ApiError
*/
export const useRepositoriesServiceDeleteRepositoryById = <TData = Common.RepositoriesServiceDeleteRepositoryByIdMutationResult, TError = unknown, TContext = unknown>(options?: Omit<UseMutationOptions<TData, TError, {
  repositoryId?: number;
}, TContext>, "mutationFn">) => useMutation<TData, TError, {
  repositoryId?: number;
}, TContext>({ mutationFn: ({ repositoryId }) => RepositoriesService.deleteRepositoryById({ repositoryId }) as unknown as Promise<TData>, ...options });
