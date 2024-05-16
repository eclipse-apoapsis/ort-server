// generated with @7nohe/openapi-react-query-codegen@1.3.0

import { UseQueryResult } from '@tanstack/react-query';
import {
  HealthService,
  InfrastructureServicesService,
  LogsService,
  OrganizationsService,
  ProductsService,
  ReportsService,
  RepositoriesService,
  SecretsService,
} from '../requests/services.gen';
export type HealthServiceGetLivenessDefaultResponse = Awaited<
  ReturnType<typeof HealthService.getLiveness>
>;
export type HealthServiceGetLivenessQueryResult<
  TData = HealthServiceGetLivenessDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useHealthServiceGetLivenessKey = 'HealthServiceGetLiveness';
export const UseHealthServiceGetLivenessKeyFn = () => [
  useHealthServiceGetLivenessKey,
];
export type ReportsServiceGetReportByRunIdAndTokenDefaultResponse = Awaited<
  ReturnType<typeof ReportsService.getReportByRunIdAndToken>
>;
export type ReportsServiceGetReportByRunIdAndTokenQueryResult<
  TData = ReportsServiceGetReportByRunIdAndTokenDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useReportsServiceGetReportByRunIdAndTokenKey =
  'ReportsServiceGetReportByRunIdAndToken';
export const UseReportsServiceGetReportByRunIdAndTokenKeyFn = (
  {
    runId,
    token,
  }: {
    runId?: number;
    token?: string;
  } = {},
  queryKey?: Array<unknown>
) => [
  useReportsServiceGetReportByRunIdAndTokenKey,
  ...(queryKey ?? [{ runId, token }]),
];
export type ReportsServiceGetReportByRunIdAndFileNameDefaultResponse = Awaited<
  ReturnType<typeof ReportsService.getReportByRunIdAndFileName>
>;
export type ReportsServiceGetReportByRunIdAndFileNameQueryResult<
  TData = ReportsServiceGetReportByRunIdAndFileNameDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useReportsServiceGetReportByRunIdAndFileNameKey =
  'ReportsServiceGetReportByRunIdAndFileName';
export const UseReportsServiceGetReportByRunIdAndFileNameKeyFn = (
  {
    fileName,
    runId,
  }: {
    fileName?: string;
    runId?: number;
  } = {},
  queryKey?: Array<unknown>
) => [
  useReportsServiceGetReportByRunIdAndFileNameKey,
  ...(queryKey ?? [{ fileName, runId }]),
];
export type OrganizationsServiceGetOrganizationsDefaultResponse = Awaited<
  ReturnType<typeof OrganizationsService.getOrganizations>
>;
export type OrganizationsServiceGetOrganizationsQueryResult<
  TData = OrganizationsServiceGetOrganizationsDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useOrganizationsServiceGetOrganizationsKey =
  'OrganizationsServiceGetOrganizations';
export const UseOrganizationsServiceGetOrganizationsKeyFn = (
  {
    limit,
    offset,
    sort,
  }: {
    limit?: number;
    offset?: number;
    sort?: string;
  } = {},
  queryKey?: Array<unknown>
) => [
  useOrganizationsServiceGetOrganizationsKey,
  ...(queryKey ?? [{ limit, offset, sort }]),
];
export type OrganizationsServiceGetOrganizationByIdDefaultResponse = Awaited<
  ReturnType<typeof OrganizationsService.getOrganizationById>
>;
export type OrganizationsServiceGetOrganizationByIdQueryResult<
  TData = OrganizationsServiceGetOrganizationByIdDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useOrganizationsServiceGetOrganizationByIdKey =
  'OrganizationsServiceGetOrganizationById';
export const UseOrganizationsServiceGetOrganizationByIdKeyFn = (
  {
    organizationId,
  }: {
    organizationId?: number;
  } = {},
  queryKey?: Array<unknown>
) => [
  useOrganizationsServiceGetOrganizationByIdKey,
  ...(queryKey ?? [{ organizationId }]),
];
export type ProductsServiceGetOrganizationProductsDefaultResponse = Awaited<
  ReturnType<typeof ProductsService.getOrganizationProducts>
>;
export type ProductsServiceGetOrganizationProductsQueryResult<
  TData = ProductsServiceGetOrganizationProductsDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useProductsServiceGetOrganizationProductsKey =
  'ProductsServiceGetOrganizationProducts';
export const UseProductsServiceGetOrganizationProductsKeyFn = (
  {
    limit,
    offset,
    organizationId,
    sort,
  }: {
    limit?: number;
    offset?: number;
    organizationId?: number;
    sort?: string;
  } = {},
  queryKey?: Array<unknown>
) => [
  useProductsServiceGetOrganizationProductsKey,
  ...(queryKey ?? [{ limit, offset, organizationId, sort }]),
];
export type ProductsServiceGetProductByIdDefaultResponse = Awaited<
  ReturnType<typeof ProductsService.getProductById>
>;
export type ProductsServiceGetProductByIdQueryResult<
  TData = ProductsServiceGetProductByIdDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useProductsServiceGetProductByIdKey =
  'ProductsServiceGetProductById';
export const UseProductsServiceGetProductByIdKeyFn = (
  {
    productId,
  }: {
    productId?: number;
  } = {},
  queryKey?: Array<unknown>
) => [useProductsServiceGetProductByIdKey, ...(queryKey ?? [{ productId }])];
export type SecretsServiceGetSecretsByOrganizationIdDefaultResponse = Awaited<
  ReturnType<typeof SecretsService.getSecretsByOrganizationId>
>;
export type SecretsServiceGetSecretsByOrganizationIdQueryResult<
  TData = SecretsServiceGetSecretsByOrganizationIdDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useSecretsServiceGetSecretsByOrganizationIdKey =
  'SecretsServiceGetSecretsByOrganizationId';
export const UseSecretsServiceGetSecretsByOrganizationIdKeyFn = (
  {
    limit,
    offset,
    organizationId,
    sort,
  }: {
    limit?: number;
    offset?: number;
    organizationId?: number;
    sort?: string;
  } = {},
  queryKey?: Array<unknown>
) => [
  useSecretsServiceGetSecretsByOrganizationIdKey,
  ...(queryKey ?? [{ limit, offset, organizationId, sort }]),
];
export type SecretsServiceGetSecretByOrganizationIdAndNameDefaultResponse =
  Awaited<ReturnType<typeof SecretsService.getSecretByOrganizationIdAndName>>;
export type SecretsServiceGetSecretByOrganizationIdAndNameQueryResult<
  TData = SecretsServiceGetSecretByOrganizationIdAndNameDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useSecretsServiceGetSecretByOrganizationIdAndNameKey =
  'SecretsServiceGetSecretByOrganizationIdAndName';
export const UseSecretsServiceGetSecretByOrganizationIdAndNameKeyFn = (
  {
    organizationId,
    secretName,
  }: {
    organizationId?: number;
    secretName?: string;
  } = {},
  queryKey?: Array<unknown>
) => [
  useSecretsServiceGetSecretByOrganizationIdAndNameKey,
  ...(queryKey ?? [{ organizationId, secretName }]),
];
export type SecretsServiceGetSecretsByProductIdDefaultResponse = Awaited<
  ReturnType<typeof SecretsService.getSecretsByProductId>
>;
export type SecretsServiceGetSecretsByProductIdQueryResult<
  TData = SecretsServiceGetSecretsByProductIdDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useSecretsServiceGetSecretsByProductIdKey =
  'SecretsServiceGetSecretsByProductId';
export const UseSecretsServiceGetSecretsByProductIdKeyFn = (
  {
    limit,
    offset,
    productId,
    sort,
  }: {
    limit?: number;
    offset?: number;
    productId?: number;
    sort?: string;
  } = {},
  queryKey?: Array<unknown>
) => [
  useSecretsServiceGetSecretsByProductIdKey,
  ...(queryKey ?? [{ limit, offset, productId, sort }]),
];
export type SecretsServiceGetSecretByProductIdAndNameDefaultResponse = Awaited<
  ReturnType<typeof SecretsService.getSecretByProductIdAndName>
>;
export type SecretsServiceGetSecretByProductIdAndNameQueryResult<
  TData = SecretsServiceGetSecretByProductIdAndNameDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useSecretsServiceGetSecretByProductIdAndNameKey =
  'SecretsServiceGetSecretByProductIdAndName';
export const UseSecretsServiceGetSecretByProductIdAndNameKeyFn = (
  {
    productId,
    secretName,
  }: {
    productId?: number;
    secretName?: string;
  } = {},
  queryKey?: Array<unknown>
) => [
  useSecretsServiceGetSecretByProductIdAndNameKey,
  ...(queryKey ?? [{ productId, secretName }]),
];
export type SecretsServiceGetSecretsByRepositoryIdDefaultResponse = Awaited<
  ReturnType<typeof SecretsService.getSecretsByRepositoryId>
>;
export type SecretsServiceGetSecretsByRepositoryIdQueryResult<
  TData = SecretsServiceGetSecretsByRepositoryIdDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useSecretsServiceGetSecretsByRepositoryIdKey =
  'SecretsServiceGetSecretsByRepositoryId';
export const UseSecretsServiceGetSecretsByRepositoryIdKeyFn = (
  {
    limit,
    offset,
    repositoryId,
    sort,
  }: {
    limit?: number;
    offset?: number;
    repositoryId?: number;
    sort?: string;
  } = {},
  queryKey?: Array<unknown>
) => [
  useSecretsServiceGetSecretsByRepositoryIdKey,
  ...(queryKey ?? [{ limit, offset, repositoryId, sort }]),
];
export type SecretsServiceGetSecretByRepositoryIdAndNameDefaultResponse =
  Awaited<ReturnType<typeof SecretsService.getSecretByRepositoryIdAndName>>;
export type SecretsServiceGetSecretByRepositoryIdAndNameQueryResult<
  TData = SecretsServiceGetSecretByRepositoryIdAndNameDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useSecretsServiceGetSecretByRepositoryIdAndNameKey =
  'SecretsServiceGetSecretByRepositoryIdAndName';
export const UseSecretsServiceGetSecretByRepositoryIdAndNameKeyFn = (
  {
    repositoryId,
    secretName,
  }: {
    repositoryId?: number;
    secretName?: string;
  } = {},
  queryKey?: Array<unknown>
) => [
  useSecretsServiceGetSecretByRepositoryIdAndNameKey,
  ...(queryKey ?? [{ repositoryId, secretName }]),
];
export type InfrastructureServicesServiceGetInfrastructureServicesByOrganizationIdDefaultResponse =
  Awaited<
    ReturnType<
      typeof InfrastructureServicesService.getInfrastructureServicesByOrganizationId
    >
  >;
export type InfrastructureServicesServiceGetInfrastructureServicesByOrganizationIdQueryResult<
  TData = InfrastructureServicesServiceGetInfrastructureServicesByOrganizationIdDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useInfrastructureServicesServiceGetInfrastructureServicesByOrganizationIdKey =
  'InfrastructureServicesServiceGetInfrastructureServicesByOrganizationId';
export const UseInfrastructureServicesServiceGetInfrastructureServicesByOrganizationIdKeyFn =
  (
    {
      limit,
      offset,
      organizationId,
      sort,
    }: {
      limit?: number;
      offset?: number;
      organizationId?: number;
      sort?: string;
    } = {},
    queryKey?: Array<unknown>
  ) => [
    useInfrastructureServicesServiceGetInfrastructureServicesByOrganizationIdKey,
    ...(queryKey ?? [{ limit, offset, organizationId, sort }]),
  ];
export type RepositoriesServiceGetRepositoriesByProductIdDefaultResponse =
  Awaited<ReturnType<typeof RepositoriesService.getRepositoriesByProductId>>;
export type RepositoriesServiceGetRepositoriesByProductIdQueryResult<
  TData = RepositoriesServiceGetRepositoriesByProductIdDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useRepositoriesServiceGetRepositoriesByProductIdKey =
  'RepositoriesServiceGetRepositoriesByProductId';
export const UseRepositoriesServiceGetRepositoriesByProductIdKeyFn = (
  {
    limit,
    offset,
    productId,
    sort,
  }: {
    limit?: number;
    offset?: number;
    productId?: number;
    sort?: string;
  } = {},
  queryKey?: Array<unknown>
) => [
  useRepositoriesServiceGetRepositoriesByProductIdKey,
  ...(queryKey ?? [{ limit, offset, productId, sort }]),
];
export type RepositoriesServiceGetRepositoryByIdDefaultResponse = Awaited<
  ReturnType<typeof RepositoriesService.getRepositoryById>
>;
export type RepositoriesServiceGetRepositoryByIdQueryResult<
  TData = RepositoriesServiceGetRepositoryByIdDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useRepositoriesServiceGetRepositoryByIdKey =
  'RepositoriesServiceGetRepositoryById';
export const UseRepositoriesServiceGetRepositoryByIdKeyFn = (
  {
    repositoryId,
  }: {
    repositoryId?: number;
  } = {},
  queryKey?: Array<unknown>
) => [
  useRepositoriesServiceGetRepositoryByIdKey,
  ...(queryKey ?? [{ repositoryId }]),
];
export type RepositoriesServiceGetOrtRunsDefaultResponse = Awaited<
  ReturnType<typeof RepositoriesService.getOrtRuns>
>;
export type RepositoriesServiceGetOrtRunsQueryResult<
  TData = RepositoriesServiceGetOrtRunsDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useRepositoriesServiceGetOrtRunsKey =
  'RepositoriesServiceGetOrtRuns';
export const UseRepositoriesServiceGetOrtRunsKeyFn = (
  {
    limit,
    offset,
    repositoryId,
    sort,
  }: {
    limit?: number;
    offset?: number;
    repositoryId?: number;
    sort?: string;
  } = {},
  queryKey?: Array<unknown>
) => [
  useRepositoriesServiceGetOrtRunsKey,
  ...(queryKey ?? [{ limit, offset, repositoryId, sort }]),
];
export type RepositoriesServiceGetOrtRunByIndexDefaultResponse = Awaited<
  ReturnType<typeof RepositoriesService.getOrtRunByIndex>
>;
export type RepositoriesServiceGetOrtRunByIndexQueryResult<
  TData = RepositoriesServiceGetOrtRunByIndexDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useRepositoriesServiceGetOrtRunByIndexKey =
  'RepositoriesServiceGetOrtRunByIndex';
export const UseRepositoriesServiceGetOrtRunByIndexKeyFn = (
  {
    ortRunIndex,
    repositoryId,
  }: {
    ortRunIndex?: number;
    repositoryId?: number;
  } = {},
  queryKey?: Array<unknown>
) => [
  useRepositoriesServiceGetOrtRunByIndexKey,
  ...(queryKey ?? [{ ortRunIndex, repositoryId }]),
];
export type LogsServiceGetLogsByRunIdDefaultResponse = Awaited<
  ReturnType<typeof LogsService.getLogsByRunId>
>;
export type LogsServiceGetLogsByRunIdQueryResult<
  TData = LogsServiceGetLogsByRunIdDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useLogsServiceGetLogsByRunIdKey = 'LogsServiceGetLogsByRunId';
export const UseLogsServiceGetLogsByRunIdKeyFn = (
  {
    level,
    runId,
    steps,
  }: {
    level?: string;
    runId?: number;
    steps?: string;
  } = {},
  queryKey?: Array<unknown>
) => [
  useLogsServiceGetLogsByRunIdKey,
  ...(queryKey ?? [{ level, runId, steps }]),
];
export type OrganizationsServicePostOrganizationsMutationResult = Awaited<
  ReturnType<typeof OrganizationsService.postOrganizations>
>;
export type ProductsServicePostProductMutationResult = Awaited<
  ReturnType<typeof ProductsService.postProduct>
>;
export type SecretsServicePostSecretForOrganizationMutationResult = Awaited<
  ReturnType<typeof SecretsService.postSecretForOrganization>
>;
export type SecretsServicePostSecretForProductMutationResult = Awaited<
  ReturnType<typeof SecretsService.postSecretForProduct>
>;
export type SecretsServicePostSecretForRepositoryMutationResult = Awaited<
  ReturnType<typeof SecretsService.postSecretForRepository>
>;
export type InfrastructureServicesServicePostInfrastructureServiceForOrganizationMutationResult =
  Awaited<
    ReturnType<
      typeof InfrastructureServicesService.postInfrastructureServiceForOrganization
    >
  >;
export type RepositoriesServiceCreateRepositoryMutationResult = Awaited<
  ReturnType<typeof RepositoriesService.createRepository>
>;
export type RepositoriesServicePostOrtRunMutationResult = Awaited<
  ReturnType<typeof RepositoriesService.postOrtRun>
>;
export type OrganizationsServicePatchOrganizationByIdMutationResult = Awaited<
  ReturnType<typeof OrganizationsService.patchOrganizationById>
>;
export type ProductsServicePatchProductByIdMutationResult = Awaited<
  ReturnType<typeof ProductsService.patchProductById>
>;
export type SecretsServicePatchSecretByOrganizationIdAndNameMutationResult =
  Awaited<ReturnType<typeof SecretsService.patchSecretByOrganizationIdAndName>>;
export type SecretsServicePatchSecretByProductIdIdAndNameMutationResult =
  Awaited<ReturnType<typeof SecretsService.patchSecretByProductIdIdAndName>>;
export type SecretsServicePatchSecretByRepositoryIdIdAndNameMutationResult =
  Awaited<ReturnType<typeof SecretsService.patchSecretByRepositoryIdIdAndName>>;
export type InfrastructureServicesServicePatchInfrastructureServiceForOrganizationIdAndNameMutationResult =
  Awaited<
    ReturnType<
      typeof InfrastructureServicesService.patchInfrastructureServiceForOrganizationIdAndName
    >
  >;
export type RepositoriesServicePatchRepositoryByIdMutationResult = Awaited<
  ReturnType<typeof RepositoriesService.patchRepositoryById>
>;
export type OrganizationsServiceDeleteOrganizationByIdMutationResult = Awaited<
  ReturnType<typeof OrganizationsService.deleteOrganizationById>
>;
export type ProductsServiceDeleteProductByIdMutationResult = Awaited<
  ReturnType<typeof ProductsService.deleteProductById>
>;
export type SecretsServiceDeleteSecretByOrganizationIdAndNameMutationResult =
  Awaited<
    ReturnType<typeof SecretsService.deleteSecretByOrganizationIdAndName>
  >;
export type SecretsServiceDeleteSecretByProductIdAndNameMutationResult =
  Awaited<ReturnType<typeof SecretsService.deleteSecretByProductIdAndName>>;
export type SecretsServiceDeleteSecretByRepositoryIdAndNameMutationResult =
  Awaited<ReturnType<typeof SecretsService.deleteSecretByRepositoryIdAndName>>;
export type InfrastructureServicesServiceDeleteInfrastructureServiceForOrganizationIdAndNameMutationResult =
  Awaited<
    ReturnType<
      typeof InfrastructureServicesService.deleteInfrastructureServiceForOrganizationIdAndName
    >
  >;
export type RepositoriesServiceDeleteRepositoryByIdMutationResult = Awaited<
  ReturnType<typeof RepositoriesService.deleteRepositoryById>
>;
