/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { CreateSecret } from '../models/CreateSecret';
import type { org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Secret_ } from '../models/org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Secret_';
import type { Secret } from '../models/Secret';
import type { UpdateSecret } from '../models/UpdateSecret';

import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';

export class SecretsService {
  /**
   * Get all secrets of an organization.
   * @param organizationId The ID of an organization.
   * @param limit The maximum number of items to retrieve. If not specified at most 20 items are retrieved.
   * @param offset The offset of the first item in the result. Together with 'limit', this can be used to implement paging.
   * @param sort Comma-separated list of fields by which the result is sorted. The listed fields must be supported by the endpoint. Putting a minus ('-') before a field name, reverts the sort order for this field. If not specified, a default sort field and sort order is used.
   * @returns org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Secret_ Success
   * @throws ApiError
   */
  public static getSecretsByOrganizationId(
    organizationId?: number,
    limit?: number,
    offset?: number,
    sort?: string
  ): CancelablePromise<org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Secret_> {
    return __request(OpenAPI, {
      method: 'GET',
      url: '/api/v1/organizations/{organizationId}/secrets',
      path: {
        organizationId: organizationId,
      },
      query: {
        limit: limit,
        offset: offset,
        sort: sort,
      },
      errors: {
        401: `Invalid Token`,
      },
    });
  }

  /**
   * Create a secret for an organization.
   * @param requestBody
   * @returns Secret Success
   * @throws ApiError
   */
  public static postSecretForOrganization(
    requestBody?: CreateSecret
  ): CancelablePromise<Secret> {
    return __request(OpenAPI, {
      method: 'POST',
      url: '/api/v1/organizations/{organizationId}/secrets',
      body: requestBody,
      mediaType: 'application/json',
      errors: {
        401: `Invalid Token`,
      },
    });
  }

  /**
   * Get details of a secret of an organization.
   * @param organizationId The organization's ID.
   * @param secretName The secret's name.
   * @returns Secret Success
   * @throws ApiError
   */
  public static getSecretByOrganizationIdAndName(
    organizationId?: number,
    secretName?: string
  ): CancelablePromise<Secret> {
    return __request(OpenAPI, {
      method: 'GET',
      url: '/api/v1/organizations/{organizationId}/secrets/{secretName}',
      path: {
        organizationId: organizationId,
        secretName: secretName,
      },
      errors: {
        401: `Invalid Token`,
      },
    });
  }

  /**
   * Delete a secret from an organization.
   * @param organizationId The organization's ID.
   * @param secretName The secret's name.
   * @returns void
   * @throws ApiError
   */
  public static deleteSecretByOrganizationIdAndName(
    organizationId?: number,
    secretName?: string
  ): CancelablePromise<void> {
    return __request(OpenAPI, {
      method: 'DELETE',
      url: '/api/v1/organizations/{organizationId}/secrets/{secretName}',
      path: {
        organizationId: organizationId,
        secretName: secretName,
      },
      errors: {
        401: `Invalid Token`,
      },
    });
  }

  /**
   * Update a secret of an organization.
   * @param organizationId The organization's ID.
   * @param secretName The secret's name.
   * @param requestBody Set the values that should be updated. To delete a value, set it explicitly to null.
   * @returns Secret Success
   * @throws ApiError
   */
  public static patchSecretByOrganizationIdAndName(
    organizationId?: number,
    secretName?: string,
    requestBody?: UpdateSecret
  ): CancelablePromise<Secret> {
    return __request(OpenAPI, {
      method: 'PATCH',
      url: '/api/v1/organizations/{organizationId}/secrets/{secretName}',
      path: {
        organizationId: organizationId,
        secretName: secretName,
      },
      body: requestBody,
      mediaType: 'application/json',
      errors: {
        401: `Invalid Token`,
      },
    });
  }

  /**
   * Get all secrets of a specific product.
   * @param productId The ID of a product.
   * @param limit The maximum number of items to retrieve. If not specified at most 20 items are retrieved.
   * @param offset The offset of the first item in the result. Together with 'limit', this can be used to implement paging.
   * @param sort Comma-separated list of fields by which the result is sorted. The listed fields must be supported by the endpoint. Putting a minus ('-') before a field name, reverts the sort order for this field. If not specified, a default sort field and sort order is used.
   * @returns org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Secret_ Success
   * @throws ApiError
   */
  public static getSecretsByProductId(
    productId?: number,
    limit?: number,
    offset?: number,
    sort?: string
  ): CancelablePromise<org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Secret_> {
    return __request(OpenAPI, {
      method: 'GET',
      url: '/api/v1/products/{productId}/secrets',
      path: {
        productId: productId,
      },
      query: {
        limit: limit,
        offset: offset,
        sort: sort,
      },
      errors: {
        401: `Invalid Token`,
      },
    });
  }

  /**
   * Create a secret for a product.
   * @param requestBody
   * @returns Secret Success
   * @throws ApiError
   */
  public static postSecretForProduct(
    requestBody?: CreateSecret
  ): CancelablePromise<Secret> {
    return __request(OpenAPI, {
      method: 'POST',
      url: '/api/v1/products/{productId}/secrets',
      body: requestBody,
      mediaType: 'application/json',
      errors: {
        401: `Invalid Token`,
      },
    });
  }

  /**
   * Get details of a secret of a product.
   * @param productId The product's ID.
   * @param secretName The secret's name.
   * @returns Secret Success
   * @throws ApiError
   */
  public static getSecretByProductIdAndName(
    productId?: number,
    secretName?: string
  ): CancelablePromise<Secret> {
    return __request(OpenAPI, {
      method: 'GET',
      url: '/api/v1/products/{productId}/secrets/{secretName}',
      path: {
        productId: productId,
        secretName: secretName,
      },
      errors: {
        401: `Invalid Token`,
      },
    });
  }

  /**
   * Delete a secret from a product.
   * @param productId The product's ID.
   * @param secretName The secret's name.
   * @returns void
   * @throws ApiError
   */
  public static deleteSecretByProductIdAndName(
    productId?: number,
    secretName?: string
  ): CancelablePromise<void> {
    return __request(OpenAPI, {
      method: 'DELETE',
      url: '/api/v1/products/{productId}/secrets/{secretName}',
      path: {
        productId: productId,
        secretName: secretName,
      },
      errors: {
        401: `Invalid Token`,
      },
    });
  }

  /**
   * Update a secret of a product.
   * @param productId The product's ID.
   * @param secretName The secret's name.
   * @param requestBody Set the values that should be updated. To delete a value, set it explicitly to null.
   * @returns Secret Success
   * @throws ApiError
   */
  public static patchSecretByProductIdIdAndName(
    productId?: number,
    secretName?: string,
    requestBody?: UpdateSecret
  ): CancelablePromise<Secret> {
    return __request(OpenAPI, {
      method: 'PATCH',
      url: '/api/v1/products/{productId}/secrets/{secretName}',
      path: {
        productId: productId,
        secretName: secretName,
      },
      body: requestBody,
      mediaType: 'application/json',
      errors: {
        401: `Invalid Token`,
      },
    });
  }

  /**
   * Get all secrets of a repository.
   * @param repositoryId The ID of a repository.
   * @param limit The maximum number of items to retrieve. If not specified at most 20 items are retrieved.
   * @param offset The offset of the first item in the result. Together with 'limit', this can be used to implement paging.
   * @param sort Comma-separated list of fields by which the result is sorted. The listed fields must be supported by the endpoint. Putting a minus ('-') before a field name, reverts the sort order for this field. If not specified, a default sort field and sort order is used.
   * @returns org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Secret_ Success
   * @throws ApiError
   */
  public static getSecretsByRepositoryId(
    repositoryId?: number,
    limit?: number,
    offset?: number,
    sort?: string
  ): CancelablePromise<org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Secret_> {
    return __request(OpenAPI, {
      method: 'GET',
      url: '/api/v1/repositories/{repositoryId}/secrets',
      path: {
        repositoryId: repositoryId,
      },
      query: {
        limit: limit,
        offset: offset,
        sort: sort,
      },
      errors: {
        401: `Invalid Token`,
      },
    });
  }

  /**
   * Create a secret for a repository.
   * @param requestBody
   * @returns Secret Success
   * @throws ApiError
   */
  public static postSecretForRepository(
    requestBody?: CreateSecret
  ): CancelablePromise<Secret> {
    return __request(OpenAPI, {
      method: 'POST',
      url: '/api/v1/repositories/{repositoryId}/secrets',
      body: requestBody,
      mediaType: 'application/json',
      errors: {
        401: `Invalid Token`,
      },
    });
  }

  /**
   * Get details of a secret of a repository.
   * @param repositoryId The repository's ID.
   * @param secretName The secret's name.
   * @returns Secret Success
   * @throws ApiError
   */
  public static getSecretByRepositoryIdAndName(
    repositoryId?: number,
    secretName?: string
  ): CancelablePromise<Secret> {
    return __request(OpenAPI, {
      method: 'GET',
      url: '/api/v1/repositories/{repositoryId}/secrets/{secretName}',
      path: {
        repositoryId: repositoryId,
        secretName: secretName,
      },
      errors: {
        401: `Invalid Token`,
      },
    });
  }

  /**
   * Delete a secret from a repository.
   * @param repositoryId The repository's ID.
   * @param secretName The secret's name.
   * @returns void
   * @throws ApiError
   */
  public static deleteSecretByRepositoryIdAndName(
    repositoryId?: number,
    secretName?: string
  ): CancelablePromise<void> {
    return __request(OpenAPI, {
      method: 'DELETE',
      url: '/api/v1/repositories/{repositoryId}/secrets/{secretName}',
      path: {
        repositoryId: repositoryId,
        secretName: secretName,
      },
      errors: {
        401: `Invalid Token`,
      },
    });
  }

  /**
   * Update a secret of a repository.
   * @param repositoryIdId The repository's ID.
   * @param secretName The secret's name.
   * @param requestBody Set the values that should be updated. To delete a value, set it explicitly to null.
   * @returns Secret Success
   * @throws ApiError
   */
  public static patchSecretByRepositoryIdIdAndName(
    repositoryIdId?: number,
    secretName?: string,
    requestBody?: UpdateSecret
  ): CancelablePromise<Secret> {
    return __request(OpenAPI, {
      method: 'PATCH',
      url: '/api/v1/repositories/{repositoryId}/secrets/{secretName}',
      path: {
        repositoryIdId: repositoryIdId,
        secretName: secretName,
      },
      body: requestBody,
      mediaType: 'application/json',
      errors: {
        401: `Invalid Token`,
      },
    });
  }
}
