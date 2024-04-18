/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
// @ts-nocheck
// generated with @7nohe/openapi-react-query-codegen@0.5.3
import {
  useQuery,
  useMutation,
  UseQueryResult,
  UseQueryOptions,
  UseMutationOptions,
  UseMutationResult,
} from '@tanstack/react-query';
import { org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Secret_ } from '../requests/models/org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Secret_';
import { org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Repository_ } from '../requests/models/org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Repository_';
import { org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Product_ } from '../requests/models/org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Product_';
import { org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_OrtRunSummary_ } from '../requests/models/org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_OrtRunSummary_';
import { org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Organization_ } from '../requests/models/org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Organization_';
import { org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_InfrastructureService_ } from '../requests/models/org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_InfrastructureService_';
import { UpdateSecret } from '../requests/models/UpdateSecret';
import { UpdateRepository } from '../requests/models/UpdateRepository';
import { UpdateProduct } from '../requests/models/UpdateProduct';
import { UpdateOrganization } from '../requests/models/UpdateOrganization';
import { UpdateInfrastructureService } from '../requests/models/UpdateInfrastructureService';
import { Secret } from '../requests/models/Secret';
import { Repository } from '../requests/models/Repository';
import { Product } from '../requests/models/Product';
import { OrtRun } from '../requests/models/OrtRun';
import { Organization } from '../requests/models/Organization';
import { Liveness } from '../requests/models/Liveness';
import { InfrastructureService } from '../requests/models/InfrastructureService';
import { CreateSecret } from '../requests/models/CreateSecret';
import { CreateRepository } from '../requests/models/CreateRepository';
import { CreateProduct } from '../requests/models/CreateProduct';
import { CreateOrtRun } from '../requests/models/CreateOrtRun';
import { CreateOrganization } from '../requests/models/CreateOrganization';
import { CreateInfrastructureService } from '../requests/models/CreateInfrastructureService';
import { SecretsService } from '../requests/services/SecretsService';
import { RepositoriesService } from '../requests/services/RepositoriesService';
import { ReportsService } from '../requests/services/ReportsService';
import { ProductsService } from '../requests/services/ProductsService';
import { OrganizationsService } from '../requests/services/OrganizationsService';
import { LogsService } from '../requests/services/LogsService';
import { InfrastructureServicesService } from '../requests/services/InfrastructureServicesService';
import { HealthService } from '../requests/services/HealthService';
export type SecretsServiceGetSecretsByOrganizationIdDefaultResponse = Awaited<
  ReturnType<typeof SecretsService.getSecretsByOrganizationId>
>;
export type SecretsServiceGetSecretsByOrganizationIdQueryResult<
  TData = SecretsServiceGetSecretsByOrganizationIdDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useSecretsServiceGetSecretsByOrganizationIdKey =
  'SecretsServiceGetSecretsByOrganizationId';
/**
 * Get all secrets of an organization.
 */
export const useSecretsServiceGetSecretsByOrganizationId = <
  TData = SecretsServiceGetSecretsByOrganizationIdDefaultResponse,
  TError = unknown,
  TQueryKey extends Array<unknown> = unknown[],
>(
  {
    organizationId,
    limit,
    offset,
    sort,
  }: {
    organizationId?: number;
    limit?: number;
    offset?: number;
    sort?: string;
  },
  queryKey?: TQueryKey,
  options?: Omit<
    UseQueryOptions<TData, TError>,
    'queryKey' | 'queryFn' | 'initialData'
  >
) =>
  useQuery<TData, TError>({
    queryKey: [
      useSecretsServiceGetSecretsByOrganizationIdKey,
      ...(queryKey ?? [{ organizationId, limit, offset, sort }]),
    ],
    queryFn: () =>
      SecretsService.getSecretsByOrganizationId(
        organizationId,
        limit,
        offset,
        sort
      ) as TData,
    ...options,
  });
export type SecretsServicePostSecretForOrganizationMutationResult = Awaited<
  ReturnType<typeof SecretsService.postSecretForOrganization>
>;
/**
 * Create a secret for an organization.
 */
export const useSecretsServicePostSecretForOrganization = <
  TData = SecretsServicePostSecretForOrganizationMutationResult,
  TError = unknown,
  TContext = unknown,
>(
  options?: Omit<
    UseMutationOptions<
      TData,
      TError,
      {
        requestBody?: CreateSecret;
      },
      TContext
    >,
    'mutationFn'
  >
) =>
  useMutation<
    TData,
    TError,
    {
      requestBody?: CreateSecret;
    },
    TContext
  >({
    mutationFn: ({ requestBody }) =>
      SecretsService.postSecretForOrganization(
        requestBody
      ) as unknown as Promise<TData>,
    ...options,
  });
export type SecretsServiceGetSecretByOrganizationIdAndNameDefaultResponse =
  Awaited<ReturnType<typeof SecretsService.getSecretByOrganizationIdAndName>>;
export type SecretsServiceGetSecretByOrganizationIdAndNameQueryResult<
  TData = SecretsServiceGetSecretByOrganizationIdAndNameDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useSecretsServiceGetSecretByOrganizationIdAndNameKey =
  'SecretsServiceGetSecretByOrganizationIdAndName';
/**
 * Get details of a secret of an organization.
 */
export const useSecretsServiceGetSecretByOrganizationIdAndName = <
  TData = SecretsServiceGetSecretByOrganizationIdAndNameDefaultResponse,
  TError = unknown,
  TQueryKey extends Array<unknown> = unknown[],
>(
  {
    organizationId,
    secretName,
  }: {
    organizationId?: number;
    secretName?: string;
  },
  queryKey?: TQueryKey,
  options?: Omit<
    UseQueryOptions<TData, TError>,
    'queryKey' | 'queryFn' | 'initialData'
  >
) =>
  useQuery<TData, TError>({
    queryKey: [
      useSecretsServiceGetSecretByOrganizationIdAndNameKey,
      ...(queryKey ?? [{ organizationId, secretName }]),
    ],
    queryFn: () =>
      SecretsService.getSecretByOrganizationIdAndName(
        organizationId,
        secretName
      ) as TData,
    ...options,
  });
export type SecretsServiceDeleteSecretByOrganizationIdAndNameMutationResult =
  Awaited<
    ReturnType<typeof SecretsService.deleteSecretByOrganizationIdAndName>
  >;
/**
 * Delete a secret from an organization.
 */
export const useSecretsServiceDeleteSecretByOrganizationIdAndName = <
  TData = SecretsServiceDeleteSecretByOrganizationIdAndNameMutationResult,
  TError = unknown,
  TContext = unknown,
>(
  options?: Omit<
    UseMutationOptions<
      TData,
      TError,
      {
        organizationId?: number;
        secretName?: string;
      },
      TContext
    >,
    'mutationFn'
  >
) =>
  useMutation<
    TData,
    TError,
    {
      organizationId?: number;
      secretName?: string;
    },
    TContext
  >({
    mutationFn: ({ organizationId, secretName }) =>
      SecretsService.deleteSecretByOrganizationIdAndName(
        organizationId,
        secretName
      ) as unknown as Promise<TData>,
    ...options,
  });
export type SecretsServicePatchSecretByOrganizationIdAndNameMutationResult =
  Awaited<ReturnType<typeof SecretsService.patchSecretByOrganizationIdAndName>>;
/**
 * Update a secret of an organization.
 */
export const useSecretsServicePatchSecretByOrganizationIdAndName = <
  TData = SecretsServicePatchSecretByOrganizationIdAndNameMutationResult,
  TError = unknown,
  TContext = unknown,
>(
  options?: Omit<
    UseMutationOptions<
      TData,
      TError,
      {
        organizationId?: number;
        secretName?: string;
        requestBody?: UpdateSecret;
      },
      TContext
    >,
    'mutationFn'
  >
) =>
  useMutation<
    TData,
    TError,
    {
      organizationId?: number;
      secretName?: string;
      requestBody?: UpdateSecret;
    },
    TContext
  >({
    mutationFn: ({ organizationId, secretName, requestBody }) =>
      SecretsService.patchSecretByOrganizationIdAndName(
        organizationId,
        secretName,
        requestBody
      ) as unknown as Promise<TData>,
    ...options,
  });
export type SecretsServiceGetSecretsByProductIdDefaultResponse = Awaited<
  ReturnType<typeof SecretsService.getSecretsByProductId>
>;
export type SecretsServiceGetSecretsByProductIdQueryResult<
  TData = SecretsServiceGetSecretsByProductIdDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useSecretsServiceGetSecretsByProductIdKey =
  'SecretsServiceGetSecretsByProductId';
/**
 * Get all secrets of a specific product.
 */
export const useSecretsServiceGetSecretsByProductId = <
  TData = SecretsServiceGetSecretsByProductIdDefaultResponse,
  TError = unknown,
  TQueryKey extends Array<unknown> = unknown[],
>(
  {
    productId,
    limit,
    offset,
    sort,
  }: {
    productId?: number;
    limit?: number;
    offset?: number;
    sort?: string;
  },
  queryKey?: TQueryKey,
  options?: Omit<
    UseQueryOptions<TData, TError>,
    'queryKey' | 'queryFn' | 'initialData'
  >
) =>
  useQuery<TData, TError>({
    queryKey: [
      useSecretsServiceGetSecretsByProductIdKey,
      ...(queryKey ?? [{ productId, limit, offset, sort }]),
    ],
    queryFn: () =>
      SecretsService.getSecretsByProductId(
        productId,
        limit,
        offset,
        sort
      ) as TData,
    ...options,
  });
export type SecretsServicePostSecretForProductMutationResult = Awaited<
  ReturnType<typeof SecretsService.postSecretForProduct>
>;
/**
 * Create a secret for a product.
 */
export const useSecretsServicePostSecretForProduct = <
  TData = SecretsServicePostSecretForProductMutationResult,
  TError = unknown,
  TContext = unknown,
>(
  options?: Omit<
    UseMutationOptions<
      TData,
      TError,
      {
        requestBody?: CreateSecret;
      },
      TContext
    >,
    'mutationFn'
  >
) =>
  useMutation<
    TData,
    TError,
    {
      requestBody?: CreateSecret;
    },
    TContext
  >({
    mutationFn: ({ requestBody }) =>
      SecretsService.postSecretForProduct(
        requestBody
      ) as unknown as Promise<TData>,
    ...options,
  });
export type SecretsServiceGetSecretByProductIdAndNameDefaultResponse = Awaited<
  ReturnType<typeof SecretsService.getSecretByProductIdAndName>
>;
export type SecretsServiceGetSecretByProductIdAndNameQueryResult<
  TData = SecretsServiceGetSecretByProductIdAndNameDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useSecretsServiceGetSecretByProductIdAndNameKey =
  'SecretsServiceGetSecretByProductIdAndName';
/**
 * Get details of a secret of a product.
 */
export const useSecretsServiceGetSecretByProductIdAndName = <
  TData = SecretsServiceGetSecretByProductIdAndNameDefaultResponse,
  TError = unknown,
  TQueryKey extends Array<unknown> = unknown[],
>(
  {
    productId,
    secretName,
  }: {
    productId?: number;
    secretName?: string;
  },
  queryKey?: TQueryKey,
  options?: Omit<
    UseQueryOptions<TData, TError>,
    'queryKey' | 'queryFn' | 'initialData'
  >
) =>
  useQuery<TData, TError>({
    queryKey: [
      useSecretsServiceGetSecretByProductIdAndNameKey,
      ...(queryKey ?? [{ productId, secretName }]),
    ],
    queryFn: () =>
      SecretsService.getSecretByProductIdAndName(
        productId,
        secretName
      ) as TData,
    ...options,
  });
export type SecretsServiceDeleteSecretByProductIdAndNameMutationResult =
  Awaited<ReturnType<typeof SecretsService.deleteSecretByProductIdAndName>>;
/**
 * Delete a secret from a product.
 */
export const useSecretsServiceDeleteSecretByProductIdAndName = <
  TData = SecretsServiceDeleteSecretByProductIdAndNameMutationResult,
  TError = unknown,
  TContext = unknown,
>(
  options?: Omit<
    UseMutationOptions<
      TData,
      TError,
      {
        productId?: number;
        secretName?: string;
      },
      TContext
    >,
    'mutationFn'
  >
) =>
  useMutation<
    TData,
    TError,
    {
      productId?: number;
      secretName?: string;
    },
    TContext
  >({
    mutationFn: ({ productId, secretName }) =>
      SecretsService.deleteSecretByProductIdAndName(
        productId,
        secretName
      ) as unknown as Promise<TData>,
    ...options,
  });
export type SecretsServicePatchSecretByProductIdIdAndNameMutationResult =
  Awaited<ReturnType<typeof SecretsService.patchSecretByProductIdIdAndName>>;
/**
 * Update a secret of a product.
 */
export const useSecretsServicePatchSecretByProductIdIdAndName = <
  TData = SecretsServicePatchSecretByProductIdIdAndNameMutationResult,
  TError = unknown,
  TContext = unknown,
>(
  options?: Omit<
    UseMutationOptions<
      TData,
      TError,
      {
        productId?: number;
        secretName?: string;
        requestBody?: UpdateSecret;
      },
      TContext
    >,
    'mutationFn'
  >
) =>
  useMutation<
    TData,
    TError,
    {
      productId?: number;
      secretName?: string;
      requestBody?: UpdateSecret;
    },
    TContext
  >({
    mutationFn: ({ productId, secretName, requestBody }) =>
      SecretsService.patchSecretByProductIdIdAndName(
        productId,
        secretName,
        requestBody
      ) as unknown as Promise<TData>,
    ...options,
  });
export type SecretsServiceGetSecretsByRepositoryIdDefaultResponse = Awaited<
  ReturnType<typeof SecretsService.getSecretsByRepositoryId>
>;
export type SecretsServiceGetSecretsByRepositoryIdQueryResult<
  TData = SecretsServiceGetSecretsByRepositoryIdDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useSecretsServiceGetSecretsByRepositoryIdKey =
  'SecretsServiceGetSecretsByRepositoryId';
/**
 * Get all secrets of a repository.
 */
export const useSecretsServiceGetSecretsByRepositoryId = <
  TData = SecretsServiceGetSecretsByRepositoryIdDefaultResponse,
  TError = unknown,
  TQueryKey extends Array<unknown> = unknown[],
>(
  {
    repositoryId,
    limit,
    offset,
    sort,
  }: {
    repositoryId?: number;
    limit?: number;
    offset?: number;
    sort?: string;
  },
  queryKey?: TQueryKey,
  options?: Omit<
    UseQueryOptions<TData, TError>,
    'queryKey' | 'queryFn' | 'initialData'
  >
) =>
  useQuery<TData, TError>({
    queryKey: [
      useSecretsServiceGetSecretsByRepositoryIdKey,
      ...(queryKey ?? [{ repositoryId, limit, offset, sort }]),
    ],
    queryFn: () =>
      SecretsService.getSecretsByRepositoryId(
        repositoryId,
        limit,
        offset,
        sort
      ) as TData,
    ...options,
  });
export type SecretsServicePostSecretForRepositoryMutationResult = Awaited<
  ReturnType<typeof SecretsService.postSecretForRepository>
>;
/**
 * Create a secret for a repository.
 */
export const useSecretsServicePostSecretForRepository = <
  TData = SecretsServicePostSecretForRepositoryMutationResult,
  TError = unknown,
  TContext = unknown,
>(
  options?: Omit<
    UseMutationOptions<
      TData,
      TError,
      {
        requestBody?: CreateSecret;
      },
      TContext
    >,
    'mutationFn'
  >
) =>
  useMutation<
    TData,
    TError,
    {
      requestBody?: CreateSecret;
    },
    TContext
  >({
    mutationFn: ({ requestBody }) =>
      SecretsService.postSecretForRepository(
        requestBody
      ) as unknown as Promise<TData>,
    ...options,
  });
export type SecretsServiceGetSecretByRepositoryIdAndNameDefaultResponse =
  Awaited<ReturnType<typeof SecretsService.getSecretByRepositoryIdAndName>>;
export type SecretsServiceGetSecretByRepositoryIdAndNameQueryResult<
  TData = SecretsServiceGetSecretByRepositoryIdAndNameDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useSecretsServiceGetSecretByRepositoryIdAndNameKey =
  'SecretsServiceGetSecretByRepositoryIdAndName';
/**
 * Get details of a secret of a repository.
 */
export const useSecretsServiceGetSecretByRepositoryIdAndName = <
  TData = SecretsServiceGetSecretByRepositoryIdAndNameDefaultResponse,
  TError = unknown,
  TQueryKey extends Array<unknown> = unknown[],
>(
  {
    repositoryId,
    secretName,
  }: {
    repositoryId?: number;
    secretName?: string;
  },
  queryKey?: TQueryKey,
  options?: Omit<
    UseQueryOptions<TData, TError>,
    'queryKey' | 'queryFn' | 'initialData'
  >
) =>
  useQuery<TData, TError>({
    queryKey: [
      useSecretsServiceGetSecretByRepositoryIdAndNameKey,
      ...(queryKey ?? [{ repositoryId, secretName }]),
    ],
    queryFn: () =>
      SecretsService.getSecretByRepositoryIdAndName(
        repositoryId,
        secretName
      ) as TData,
    ...options,
  });
export type SecretsServiceDeleteSecretByRepositoryIdAndNameMutationResult =
  Awaited<ReturnType<typeof SecretsService.deleteSecretByRepositoryIdAndName>>;
/**
 * Delete a secret from a repository.
 */
export const useSecretsServiceDeleteSecretByRepositoryIdAndName = <
  TData = SecretsServiceDeleteSecretByRepositoryIdAndNameMutationResult,
  TError = unknown,
  TContext = unknown,
>(
  options?: Omit<
    UseMutationOptions<
      TData,
      TError,
      {
        repositoryId?: number;
        secretName?: string;
      },
      TContext
    >,
    'mutationFn'
  >
) =>
  useMutation<
    TData,
    TError,
    {
      repositoryId?: number;
      secretName?: string;
    },
    TContext
  >({
    mutationFn: ({ repositoryId, secretName }) =>
      SecretsService.deleteSecretByRepositoryIdAndName(
        repositoryId,
        secretName
      ) as unknown as Promise<TData>,
    ...options,
  });
export type SecretsServicePatchSecretByRepositoryIdIdAndNameMutationResult =
  Awaited<ReturnType<typeof SecretsService.patchSecretByRepositoryIdIdAndName>>;
/**
 * Update a secret of a repository.
 */
export const useSecretsServicePatchSecretByRepositoryIdIdAndName = <
  TData = SecretsServicePatchSecretByRepositoryIdIdAndNameMutationResult,
  TError = unknown,
  TContext = unknown,
>(
  options?: Omit<
    UseMutationOptions<
      TData,
      TError,
      {
        repositoryIdId?: number;
        secretName?: string;
        requestBody?: UpdateSecret;
      },
      TContext
    >,
    'mutationFn'
  >
) =>
  useMutation<
    TData,
    TError,
    {
      repositoryIdId?: number;
      secretName?: string;
      requestBody?: UpdateSecret;
    },
    TContext
  >({
    mutationFn: ({ repositoryIdId, secretName, requestBody }) =>
      SecretsService.patchSecretByRepositoryIdIdAndName(
        repositoryIdId,
        secretName,
        requestBody
      ) as unknown as Promise<TData>,
    ...options,
  });
export type RepositoriesServiceGetRepositoriesByProductIdDefaultResponse =
  Awaited<ReturnType<typeof RepositoriesService.getRepositoriesByProductId>>;
export type RepositoriesServiceGetRepositoriesByProductIdQueryResult<
  TData = RepositoriesServiceGetRepositoriesByProductIdDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useRepositoriesServiceGetRepositoriesByProductIdKey =
  'RepositoriesServiceGetRepositoriesByProductId';
/**
 * Get all repositories of a product.
 */
export const useRepositoriesServiceGetRepositoriesByProductId = <
  TData = RepositoriesServiceGetRepositoriesByProductIdDefaultResponse,
  TError = unknown,
  TQueryKey extends Array<unknown> = unknown[],
>(
  {
    productId,
    limit,
    offset,
    sort,
  }: {
    productId?: number;
    limit?: number;
    offset?: number;
    sort?: string;
  },
  queryKey?: TQueryKey,
  options?: Omit<
    UseQueryOptions<TData, TError>,
    'queryKey' | 'queryFn' | 'initialData'
  >
) =>
  useQuery<TData, TError>({
    queryKey: [
      useRepositoriesServiceGetRepositoriesByProductIdKey,
      ...(queryKey ?? [{ productId, limit, offset, sort }]),
    ],
    queryFn: () =>
      RepositoriesService.getRepositoriesByProductId(
        productId,
        limit,
        offset,
        sort
      ) as TData,
    ...options,
  });
export type RepositoriesServiceCreateRepositoryMutationResult = Awaited<
  ReturnType<typeof RepositoriesService.createRepository>
>;
/**
 * Create a repository for a product.
 */
export const useRepositoriesServiceCreateRepository = <
  TData = RepositoriesServiceCreateRepositoryMutationResult,
  TError = unknown,
  TContext = unknown,
>(
  options?: Omit<
    UseMutationOptions<
      TData,
      TError,
      {
        productId?: number;
        requestBody?: CreateRepository;
      },
      TContext
    >,
    'mutationFn'
  >
) =>
  useMutation<
    TData,
    TError,
    {
      productId?: number;
      requestBody?: CreateRepository;
    },
    TContext
  >({
    mutationFn: ({ productId, requestBody }) =>
      RepositoriesService.createRepository(
        productId,
        requestBody
      ) as unknown as Promise<TData>,
    ...options,
  });
export type RepositoriesServiceGetRepositoryByIdDefaultResponse = Awaited<
  ReturnType<typeof RepositoriesService.getRepositoryById>
>;
export type RepositoriesServiceGetRepositoryByIdQueryResult<
  TData = RepositoriesServiceGetRepositoryByIdDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useRepositoriesServiceGetRepositoryByIdKey =
  'RepositoriesServiceGetRepositoryById';
/**
 * Get details of a repository.
 */
export const useRepositoriesServiceGetRepositoryById = <
  TData = RepositoriesServiceGetRepositoryByIdDefaultResponse,
  TError = unknown,
  TQueryKey extends Array<unknown> = unknown[],
>(
  {
    repositoryId,
  }: {
    repositoryId?: number;
  },
  queryKey?: TQueryKey,
  options?: Omit<
    UseQueryOptions<TData, TError>,
    'queryKey' | 'queryFn' | 'initialData'
  >
) =>
  useQuery<TData, TError>({
    queryKey: [
      useRepositoriesServiceGetRepositoryByIdKey,
      ...(queryKey ?? [{ repositoryId }]),
    ],
    queryFn: () => RepositoriesService.getRepositoryById(repositoryId) as TData,
    ...options,
  });
export type RepositoriesServiceDeleteRepositoryByIdMutationResult = Awaited<
  ReturnType<typeof RepositoriesService.deleteRepositoryById>
>;
/**
 * Delete a repository.
 */
export const useRepositoriesServiceDeleteRepositoryById = <
  TData = RepositoriesServiceDeleteRepositoryByIdMutationResult,
  TError = unknown,
  TContext = unknown,
>(
  options?: Omit<
    UseMutationOptions<
      TData,
      TError,
      {
        repositoryId?: number;
      },
      TContext
    >,
    'mutationFn'
  >
) =>
  useMutation<
    TData,
    TError,
    {
      repositoryId?: number;
    },
    TContext
  >({
    mutationFn: ({ repositoryId }) =>
      RepositoriesService.deleteRepositoryById(
        repositoryId
      ) as unknown as Promise<TData>,
    ...options,
  });
export type RepositoriesServicePatchRepositoryByIdMutationResult = Awaited<
  ReturnType<typeof RepositoriesService.patchRepositoryById>
>;
/**
 * Update a repository.
 */
export const useRepositoriesServicePatchRepositoryById = <
  TData = RepositoriesServicePatchRepositoryByIdMutationResult,
  TError = unknown,
  TContext = unknown,
>(
  options?: Omit<
    UseMutationOptions<
      TData,
      TError,
      {
        repositoryId?: number;
        requestBody?: UpdateRepository;
      },
      TContext
    >,
    'mutationFn'
  >
) =>
  useMutation<
    TData,
    TError,
    {
      repositoryId?: number;
      requestBody?: UpdateRepository;
    },
    TContext
  >({
    mutationFn: ({ repositoryId, requestBody }) =>
      RepositoriesService.patchRepositoryById(
        repositoryId,
        requestBody
      ) as unknown as Promise<TData>,
    ...options,
  });
export type RepositoriesServiceGetOrtRunsDefaultResponse = Awaited<
  ReturnType<typeof RepositoriesService.getOrtRuns>
>;
export type RepositoriesServiceGetOrtRunsQueryResult<
  TData = RepositoriesServiceGetOrtRunsDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useRepositoriesServiceGetOrtRunsKey =
  'RepositoriesServiceGetOrtRuns';
/**
 * Get all ORT runs of a repository.
 */
export const useRepositoriesServiceGetOrtRuns = <
  TData = RepositoriesServiceGetOrtRunsDefaultResponse,
  TError = unknown,
  TQueryKey extends Array<unknown> = unknown[],
>(
  {
    repositoryId,
    limit,
    offset,
    sort,
  }: {
    repositoryId?: number;
    limit?: number;
    offset?: number;
    sort?: string;
  },
  queryKey?: TQueryKey,
  options?: Omit<
    UseQueryOptions<TData, TError>,
    'queryKey' | 'queryFn' | 'initialData'
  >
) =>
  useQuery<TData, TError>({
    queryKey: [
      useRepositoriesServiceGetOrtRunsKey,
      ...(queryKey ?? [{ repositoryId, limit, offset, sort }]),
    ],
    queryFn: () =>
      RepositoriesService.getOrtRuns(
        repositoryId,
        limit,
        offset,
        sort
      ) as TData,
    ...options,
  });
export type RepositoriesServicePostOrtRunMutationResult = Awaited<
  ReturnType<typeof RepositoriesService.postOrtRun>
>;
/**
 * Create an ORT run for a repository.
 */
export const useRepositoriesServicePostOrtRun = <
  TData = RepositoriesServicePostOrtRunMutationResult,
  TError = unknown,
  TContext = unknown,
>(
  options?: Omit<
    UseMutationOptions<
      TData,
      TError,
      {
        repositoryId?: number;
        requestBody?: CreateOrtRun;
      },
      TContext
    >,
    'mutationFn'
  >
) =>
  useMutation<
    TData,
    TError,
    {
      repositoryId?: number;
      requestBody?: CreateOrtRun;
    },
    TContext
  >({
    mutationFn: ({ repositoryId, requestBody }) =>
      RepositoriesService.postOrtRun(
        repositoryId,
        requestBody
      ) as unknown as Promise<TData>,
    ...options,
  });
export type RepositoriesServiceGetOrtRunByIndexDefaultResponse = Awaited<
  ReturnType<typeof RepositoriesService.getOrtRunByIndex>
>;
export type RepositoriesServiceGetOrtRunByIndexQueryResult<
  TData = RepositoriesServiceGetOrtRunByIndexDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useRepositoriesServiceGetOrtRunByIndexKey =
  'RepositoriesServiceGetOrtRunByIndex';
/**
 * Get details of an ORT run of a repository.
 */
export const useRepositoriesServiceGetOrtRunByIndex = <
  TData = RepositoriesServiceGetOrtRunByIndexDefaultResponse,
  TError = unknown,
  TQueryKey extends Array<unknown> = unknown[],
>(
  {
    repositoryId,
    ortRunIndex,
  }: {
    repositoryId?: number;
    ortRunIndex?: number;
  },
  queryKey?: TQueryKey,
  options?: Omit<
    UseQueryOptions<TData, TError>,
    'queryKey' | 'queryFn' | 'initialData'
  >
) =>
  useQuery<TData, TError>({
    queryKey: [
      useRepositoriesServiceGetOrtRunByIndexKey,
      ...(queryKey ?? [{ repositoryId, ortRunIndex }]),
    ],
    queryFn: () =>
      RepositoriesService.getOrtRunByIndex(repositoryId, ortRunIndex) as TData,
    ...options,
  });
export type ReportsServiceGetReportByRunIdAndTokenDefaultResponse = Awaited<
  ReturnType<typeof ReportsService.getReportByRunIdAndToken>
>;
export type ReportsServiceGetReportByRunIdAndTokenQueryResult<
  TData = ReportsServiceGetReportByRunIdAndTokenDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useReportsServiceGetReportByRunIdAndTokenKey =
  'ReportsServiceGetReportByRunIdAndToken';
/**
 * Download a report of an ORT run using a token. This endpoint does not require authentication.
 */
export const useReportsServiceGetReportByRunIdAndToken = <
  TData = ReportsServiceGetReportByRunIdAndTokenDefaultResponse,
  TError = unknown,
  TQueryKey extends Array<unknown> = unknown[],
>(
  {
    runId,
    token,
  }: {
    runId?: number;
    token?: string;
  },
  queryKey?: TQueryKey,
  options?: Omit<
    UseQueryOptions<TData, TError>,
    'queryKey' | 'queryFn' | 'initialData'
  >
) =>
  useQuery<TData, TError>({
    queryKey: [
      useReportsServiceGetReportByRunIdAndTokenKey,
      ...(queryKey ?? [{ runId, token }]),
    ],
    queryFn: () =>
      ReportsService.getReportByRunIdAndToken(runId, token) as TData,
    ...options,
  });
export type ReportsServiceGetReportByRunIdAndFileNameDefaultResponse = Awaited<
  ReturnType<typeof ReportsService.getReportByRunIdAndFileName>
>;
export type ReportsServiceGetReportByRunIdAndFileNameQueryResult<
  TData = ReportsServiceGetReportByRunIdAndFileNameDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useReportsServiceGetReportByRunIdAndFileNameKey =
  'ReportsServiceGetReportByRunIdAndFileName';
/**
 * Download a report of an ORT run.
 */
export const useReportsServiceGetReportByRunIdAndFileName = <
  TData = ReportsServiceGetReportByRunIdAndFileNameDefaultResponse,
  TError = unknown,
  TQueryKey extends Array<unknown> = unknown[],
>(
  {
    runId,
    fileName,
  }: {
    runId?: number;
    fileName?: string;
  },
  queryKey?: TQueryKey,
  options?: Omit<
    UseQueryOptions<TData, TError>,
    'queryKey' | 'queryFn' | 'initialData'
  >
) =>
  useQuery<TData, TError>({
    queryKey: [
      useReportsServiceGetReportByRunIdAndFileNameKey,
      ...(queryKey ?? [{ runId, fileName }]),
    ],
    queryFn: () =>
      ReportsService.getReportByRunIdAndFileName(runId, fileName) as TData,
    ...options,
  });
export type ProductsServiceGetOrganizationProductsDefaultResponse = Awaited<
  ReturnType<typeof ProductsService.getOrganizationProducts>
>;
export type ProductsServiceGetOrganizationProductsQueryResult<
  TData = ProductsServiceGetOrganizationProductsDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useProductsServiceGetOrganizationProductsKey =
  'ProductsServiceGetOrganizationProducts';
/**
 * Get all products of an organization.
 */
export const useProductsServiceGetOrganizationProducts = <
  TData = ProductsServiceGetOrganizationProductsDefaultResponse,
  TError = unknown,
  TQueryKey extends Array<unknown> = unknown[],
>(
  {
    organizationId,
    limit,
    offset,
    sort,
  }: {
    organizationId?: number;
    limit?: number;
    offset?: number;
    sort?: string;
  },
  queryKey?: TQueryKey,
  options?: Omit<
    UseQueryOptions<TData, TError>,
    'queryKey' | 'queryFn' | 'initialData'
  >
) =>
  useQuery<TData, TError>({
    queryKey: [
      useProductsServiceGetOrganizationProductsKey,
      ...(queryKey ?? [{ organizationId, limit, offset, sort }]),
    ],
    queryFn: () =>
      ProductsService.getOrganizationProducts(
        organizationId,
        limit,
        offset,
        sort
      ) as TData,
    ...options,
  });
export type ProductsServicePostProductMutationResult = Awaited<
  ReturnType<typeof ProductsService.postProduct>
>;
/**
 * Create a product for an organization.
 */
export const useProductsServicePostProduct = <
  TData = ProductsServicePostProductMutationResult,
  TError = unknown,
  TContext = unknown,
>(
  options?: Omit<
    UseMutationOptions<
      TData,
      TError,
      {
        organizationId?: number;
        requestBody?: CreateProduct;
      },
      TContext
    >,
    'mutationFn'
  >
) =>
  useMutation<
    TData,
    TError,
    {
      organizationId?: number;
      requestBody?: CreateProduct;
    },
    TContext
  >({
    mutationFn: ({ organizationId, requestBody }) =>
      ProductsService.postProduct(
        organizationId,
        requestBody
      ) as unknown as Promise<TData>,
    ...options,
  });
export type ProductsServiceGetProductByIdDefaultResponse = Awaited<
  ReturnType<typeof ProductsService.getProductById>
>;
export type ProductsServiceGetProductByIdQueryResult<
  TData = ProductsServiceGetProductByIdDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useProductsServiceGetProductByIdKey =
  'ProductsServiceGetProductById';
/**
 * Get details of a product.
 */
export const useProductsServiceGetProductById = <
  TData = ProductsServiceGetProductByIdDefaultResponse,
  TError = unknown,
  TQueryKey extends Array<unknown> = unknown[],
>(
  {
    productId,
  }: {
    productId?: number;
  },
  queryKey?: TQueryKey,
  options?: Omit<
    UseQueryOptions<TData, TError>,
    'queryKey' | 'queryFn' | 'initialData'
  >
) =>
  useQuery<TData, TError>({
    queryKey: [
      useProductsServiceGetProductByIdKey,
      ...(queryKey ?? [{ productId }]),
    ],
    queryFn: () => ProductsService.getProductById(productId) as TData,
    ...options,
  });
export type ProductsServiceDeleteProductByIdMutationResult = Awaited<
  ReturnType<typeof ProductsService.deleteProductById>
>;
/**
 * Delete a product.
 */
export const useProductsServiceDeleteProductById = <
  TData = ProductsServiceDeleteProductByIdMutationResult,
  TError = unknown,
  TContext = unknown,
>(
  options?: Omit<
    UseMutationOptions<
      TData,
      TError,
      {
        productId?: number;
      },
      TContext
    >,
    'mutationFn'
  >
) =>
  useMutation<
    TData,
    TError,
    {
      productId?: number;
    },
    TContext
  >({
    mutationFn: ({ productId }) =>
      ProductsService.deleteProductById(productId) as unknown as Promise<TData>,
    ...options,
  });
export type ProductsServicePatchProductByIdMutationResult = Awaited<
  ReturnType<typeof ProductsService.patchProductById>
>;
/**
 * Update a product.
 */
export const useProductsServicePatchProductById = <
  TData = ProductsServicePatchProductByIdMutationResult,
  TError = unknown,
  TContext = unknown,
>(
  options?: Omit<
    UseMutationOptions<
      TData,
      TError,
      {
        productId?: number;
        requestBody?: UpdateProduct;
      },
      TContext
    >,
    'mutationFn'
  >
) =>
  useMutation<
    TData,
    TError,
    {
      productId?: number;
      requestBody?: UpdateProduct;
    },
    TContext
  >({
    mutationFn: ({ productId, requestBody }) =>
      ProductsService.patchProductById(
        productId,
        requestBody
      ) as unknown as Promise<TData>,
    ...options,
  });
export type OrganizationsServiceGetOrganizationsDefaultResponse = Awaited<
  ReturnType<typeof OrganizationsService.getOrganizations>
>;
export type OrganizationsServiceGetOrganizationsQueryResult<
  TData = OrganizationsServiceGetOrganizationsDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useOrganizationsServiceGetOrganizationsKey =
  'OrganizationsServiceGetOrganizations';
/**
 * Get all organizations.
 */
export const useOrganizationsServiceGetOrganizations = <
  TData = OrganizationsServiceGetOrganizationsDefaultResponse,
  TError = unknown,
  TQueryKey extends Array<unknown> = unknown[],
>(
  {
    limit,
    offset,
    sort,
  }: {
    limit?: number;
    offset?: number;
    sort?: string;
  },
  queryKey?: TQueryKey,
  options?: Omit<
    UseQueryOptions<TData, TError>,
    'queryKey' | 'queryFn' | 'initialData'
  >
) =>
  useQuery<TData, TError>({
    queryKey: [
      useOrganizationsServiceGetOrganizationsKey,
      ...(queryKey ?? [{ limit, offset, sort }]),
    ],
    queryFn: () =>
      OrganizationsService.getOrganizations(limit, offset, sort) as TData,
    ...options,
  });
export type OrganizationsServicePostOrganizationsMutationResult = Awaited<
  ReturnType<typeof OrganizationsService.postOrganizations>
>;
/**
 * Create an organization.
 */
export const useOrganizationsServicePostOrganizations = <
  TData = OrganizationsServicePostOrganizationsMutationResult,
  TError = unknown,
  TContext = unknown,
>(
  options?: Omit<
    UseMutationOptions<
      TData,
      TError,
      {
        requestBody?: CreateOrganization;
      },
      TContext
    >,
    'mutationFn'
  >
) =>
  useMutation<
    TData,
    TError,
    {
      requestBody?: CreateOrganization;
    },
    TContext
  >({
    mutationFn: ({ requestBody }) =>
      OrganizationsService.postOrganizations(
        requestBody
      ) as unknown as Promise<TData>,
    ...options,
  });
export type OrganizationsServiceGetOrganizationByIdDefaultResponse = Awaited<
  ReturnType<typeof OrganizationsService.getOrganizationById>
>;
export type OrganizationsServiceGetOrganizationByIdQueryResult<
  TData = OrganizationsServiceGetOrganizationByIdDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useOrganizationsServiceGetOrganizationByIdKey =
  'OrganizationsServiceGetOrganizationById';
/**
 * Get details of an organization.
 */
export const useOrganizationsServiceGetOrganizationById = <
  TData = OrganizationsServiceGetOrganizationByIdDefaultResponse,
  TError = unknown,
  TQueryKey extends Array<unknown> = unknown[],
>(
  {
    organizationId,
  }: {
    organizationId?: number;
  },
  queryKey?: TQueryKey,
  options?: Omit<
    UseQueryOptions<TData, TError>,
    'queryKey' | 'queryFn' | 'initialData'
  >
) =>
  useQuery<TData, TError>({
    queryKey: [
      useOrganizationsServiceGetOrganizationByIdKey,
      ...(queryKey ?? [{ organizationId }]),
    ],
    queryFn: () =>
      OrganizationsService.getOrganizationById(organizationId) as TData,
    ...options,
  });
export type OrganizationsServiceDeleteOrganizationByIdMutationResult = Awaited<
  ReturnType<typeof OrganizationsService.deleteOrganizationById>
>;
/**
 * Delete an organization.
 */
export const useOrganizationsServiceDeleteOrganizationById = <
  TData = OrganizationsServiceDeleteOrganizationByIdMutationResult,
  TError = unknown,
  TContext = unknown,
>(
  options?: Omit<
    UseMutationOptions<
      TData,
      TError,
      {
        organizationId?: number;
      },
      TContext
    >,
    'mutationFn'
  >
) =>
  useMutation<
    TData,
    TError,
    {
      organizationId?: number;
    },
    TContext
  >({
    mutationFn: ({ organizationId }) =>
      OrganizationsService.deleteOrganizationById(
        organizationId
      ) as unknown as Promise<TData>,
    ...options,
  });
export type OrganizationsServicePatchOrganizationByIdMutationResult = Awaited<
  ReturnType<typeof OrganizationsService.patchOrganizationById>
>;
/**
 * Update an organization.
 */
export const useOrganizationsServicePatchOrganizationById = <
  TData = OrganizationsServicePatchOrganizationByIdMutationResult,
  TError = unknown,
  TContext = unknown,
>(
  options?: Omit<
    UseMutationOptions<
      TData,
      TError,
      {
        organizationId?: number;
        requestBody?: UpdateOrganization;
      },
      TContext
    >,
    'mutationFn'
  >
) =>
  useMutation<
    TData,
    TError,
    {
      organizationId?: number;
      requestBody?: UpdateOrganization;
    },
    TContext
  >({
    mutationFn: ({ organizationId, requestBody }) =>
      OrganizationsService.patchOrganizationById(
        organizationId,
        requestBody
      ) as unknown as Promise<TData>,
    ...options,
  });
export type LogsServiceGetLogsByRunIdDefaultResponse = Awaited<
  ReturnType<typeof LogsService.getLogsByRunId>
>;
export type LogsServiceGetLogsByRunIdQueryResult<
  TData = LogsServiceGetLogsByRunIdDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useLogsServiceGetLogsByRunIdKey = 'LogsServiceGetLogsByRunId';
/**
 * Download an archive with selected logs of an ORT run.
 */
export const useLogsServiceGetLogsByRunId = <
  TData = LogsServiceGetLogsByRunIdDefaultResponse,
  TError = unknown,
  TQueryKey extends Array<unknown> = unknown[],
>(
  {
    runId,
    level,
    steps,
  }: {
    runId?: number;
    level?: string;
    steps?: string;
  },
  queryKey?: TQueryKey,
  options?: Omit<
    UseQueryOptions<TData, TError>,
    'queryKey' | 'queryFn' | 'initialData'
  >
) =>
  useQuery<TData, TError>({
    queryKey: [
      useLogsServiceGetLogsByRunIdKey,
      ...(queryKey ?? [{ runId, level, steps }]),
    ],
    queryFn: () => LogsService.getLogsByRunId(runId, level, steps) as TData,
    ...options,
  });
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
/**
 * List all infrastructure services of an organization.
 */
export const useInfrastructureServicesServiceGetInfrastructureServicesByOrganizationId =
  <
    TData = InfrastructureServicesServiceGetInfrastructureServicesByOrganizationIdDefaultResponse,
    TError = unknown,
    TQueryKey extends Array<unknown> = unknown[],
  >(
    {
      organizationId,
      limit,
      offset,
      sort,
    }: {
      organizationId?: number;
      limit?: number;
      offset?: number;
      sort?: string;
    },
    queryKey?: TQueryKey,
    options?: Omit<
      UseQueryOptions<TData, TError>,
      'queryKey' | 'queryFn' | 'initialData'
    >
  ) =>
    useQuery<TData, TError>({
      queryKey: [
        useInfrastructureServicesServiceGetInfrastructureServicesByOrganizationIdKey,
        ...(queryKey ?? [{ organizationId, limit, offset, sort }]),
      ],
      queryFn: () =>
        InfrastructureServicesService.getInfrastructureServicesByOrganizationId(
          organizationId,
          limit,
          offset,
          sort
        ) as TData,
      ...options,
    });
export type InfrastructureServicesServicePostInfrastructureServiceForOrganizationMutationResult =
  Awaited<
    ReturnType<
      typeof InfrastructureServicesService.postInfrastructureServiceForOrganization
    >
  >;
/**
 * Create an infrastructure service for an organization.
 */
export const useInfrastructureServicesServicePostInfrastructureServiceForOrganization =
  <
    TData = InfrastructureServicesServicePostInfrastructureServiceForOrganizationMutationResult,
    TError = unknown,
    TContext = unknown,
  >(
    options?: Omit<
      UseMutationOptions<
        TData,
        TError,
        {
          requestBody?: CreateInfrastructureService;
        },
        TContext
      >,
      'mutationFn'
    >
  ) =>
    useMutation<
      TData,
      TError,
      {
        requestBody?: CreateInfrastructureService;
      },
      TContext
    >({
      mutationFn: ({ requestBody }) =>
        InfrastructureServicesService.postInfrastructureServiceForOrganization(
          requestBody
        ) as unknown as Promise<TData>,
      ...options,
    });
export type InfrastructureServicesServiceDeleteInfrastructureServiceForOrganizationIdAndNameMutationResult =
  Awaited<
    ReturnType<
      typeof InfrastructureServicesService.deleteInfrastructureServiceForOrganizationIdAndName
    >
  >;
/**
 * Delete an infrastructure service from an organization.
 */
export const useInfrastructureServicesServiceDeleteInfrastructureServiceForOrganizationIdAndName =
  <
    TData = InfrastructureServicesServiceDeleteInfrastructureServiceForOrganizationIdAndNameMutationResult,
    TError = unknown,
    TContext = unknown,
  >(
    options?: Omit<
      UseMutationOptions<
        TData,
        TError,
        {
          organizationId?: number;
          serviceName?: string;
        },
        TContext
      >,
      'mutationFn'
    >
  ) =>
    useMutation<
      TData,
      TError,
      {
        organizationId?: number;
        serviceName?: string;
      },
      TContext
    >({
      mutationFn: ({ organizationId, serviceName }) =>
        InfrastructureServicesService.deleteInfrastructureServiceForOrganizationIdAndName(
          organizationId,
          serviceName
        ) as unknown as Promise<TData>,
      ...options,
    });
export type InfrastructureServicesServicePatchInfrastructureServiceForOrganizationIdAndNameMutationResult =
  Awaited<
    ReturnType<
      typeof InfrastructureServicesService.patchInfrastructureServiceForOrganizationIdAndName
    >
  >;
/**
 * Update an infrastructure service for an organization.
 */
export const useInfrastructureServicesServicePatchInfrastructureServiceForOrganizationIdAndName =
  <
    TData = InfrastructureServicesServicePatchInfrastructureServiceForOrganizationIdAndNameMutationResult,
    TError = unknown,
    TContext = unknown,
  >(
    options?: Omit<
      UseMutationOptions<
        TData,
        TError,
        {
          organizationId?: number;
          serviceName?: string;
          requestBody?: UpdateInfrastructureService;
        },
        TContext
      >,
      'mutationFn'
    >
  ) =>
    useMutation<
      TData,
      TError,
      {
        organizationId?: number;
        serviceName?: string;
        requestBody?: UpdateInfrastructureService;
      },
      TContext
    >({
      mutationFn: ({ organizationId, serviceName, requestBody }) =>
        InfrastructureServicesService.patchInfrastructureServiceForOrganizationIdAndName(
          organizationId,
          serviceName,
          requestBody
        ) as unknown as Promise<TData>,
      ...options,
    });
export type HealthServiceGetLivenessDefaultResponse = Awaited<
  ReturnType<typeof HealthService.getLiveness>
>;
export type HealthServiceGetLivenessQueryResult<
  TData = HealthServiceGetLivenessDefaultResponse,
  TError = unknown,
> = UseQueryResult<TData, TError>;
export const useHealthServiceGetLivenessKey = 'HealthServiceGetLiveness';
/**
 * Get the health of the ORT server.
 */
export const useHealthServiceGetLiveness = <
  TData = HealthServiceGetLivenessDefaultResponse,
  TError = unknown,
  TQueryKey extends Array<unknown> = unknown[],
>(
  queryKey?: TQueryKey,
  options?: Omit<
    UseQueryOptions<TData, TError>,
    'queryKey' | 'queryFn' | 'initialData'
  >
) =>
  useQuery<TData, TError>({
    queryKey: [useHealthServiceGetLivenessKey, ...(queryKey ?? [])],
    queryFn: () => HealthService.getLiveness() as TData,
    ...options,
  });
