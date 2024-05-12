/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { CreateOrtRun } from '../models/CreateOrtRun';
import type { CreateRepository } from '../models/CreateRepository';
import type { org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_OrtRunSummary_ } from '../models/org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_OrtRunSummary_';
import type { org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Repository_ } from '../models/org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Repository_';
import type { OrtRun } from '../models/OrtRun';
import type { Repository } from '../models/Repository';
import type { UpdateRepository } from '../models/UpdateRepository';

import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';

export class RepositoriesService {
  /**
   * Get all repositories of a product.
   * @param productId The product's ID.
   * @param limit The maximum number of items to retrieve. If not specified at most 20 items are retrieved.
   * @param offset The offset of the first item in the result. Together with 'limit', this can be used to implement paging.
   * @param sort Comma-separated list of fields by which the result is sorted. The listed fields must be supported by the endpoint. Putting a minus ('-') before a field name, reverts the sort order for this field. If not specified, a default sort field and sort order is used.
   * @returns org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Repository_ Success
   * @throws ApiError
   */
  public static getRepositoriesByProductId(
    productId?: number,
    limit?: number,
    offset?: number,
    sort?: string
  ): CancelablePromise<org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Repository_> {
    return __request(OpenAPI, {
      method: 'GET',
      url: '/api/v1/products/{productId}/repositories',
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
   * Create a repository for a product.
   * @param productId The product's ID.
   * @param requestBody
   * @returns Repository Success
   * @throws ApiError
   */
  public static createRepository(
    productId?: number,
    requestBody?: CreateRepository
  ): CancelablePromise<Repository> {
    return __request(OpenAPI, {
      method: 'POST',
      url: '/api/v1/products/{productId}/repositories',
      path: {
        productId: productId,
      },
      body: requestBody,
      mediaType: 'application/json',
      errors: {
        401: `Invalid Token`,
      },
    });
  }

  /**
   * Get details of a repository.
   * @param repositoryId The repository's ID.
   * @returns Repository Success
   * @throws ApiError
   */
  public static getRepositoryById(
    repositoryId?: number
  ): CancelablePromise<Repository> {
    return __request(OpenAPI, {
      method: 'GET',
      url: '/api/v1/repositories/{repositoryId}',
      path: {
        repositoryId: repositoryId,
      },
      errors: {
        401: `Invalid Token`,
      },
    });
  }

  /**
   * Delete a repository.
   * @param repositoryId The repository's ID.
   * @returns void
   * @throws ApiError
   */
  public static deleteRepositoryById(
    repositoryId?: number
  ): CancelablePromise<void> {
    return __request(OpenAPI, {
      method: 'DELETE',
      url: '/api/v1/repositories/{repositoryId}',
      path: {
        repositoryId: repositoryId,
      },
      errors: {
        401: `Invalid Token`,
      },
    });
  }

  /**
   * Update a repository.
   * @param repositoryId The repository's ID.
   * @param requestBody Set the values that should be updated. To delete a value, set it explicitly to null.
   * @returns Repository Success
   * @throws ApiError
   */
  public static patchRepositoryById(
    repositoryId?: number,
    requestBody?: UpdateRepository
  ): CancelablePromise<Repository> {
    return __request(OpenAPI, {
      method: 'PATCH',
      url: '/api/v1/repositories/{repositoryId}',
      path: {
        repositoryId: repositoryId,
      },
      body: requestBody,
      mediaType: 'application/json',
      errors: {
        401: `Invalid Token`,
      },
    });
  }

  /**
   * Get all ORT runs of a repository.
   * @param repositoryId The repository's ID.
   * @param limit The maximum number of items to retrieve. If not specified at most 20 items are retrieved.
   * @param offset The offset of the first item in the result. Together with 'limit', this can be used to implement paging.
   * @param sort Comma-separated list of fields by which the result is sorted. The listed fields must be supported by the endpoint. Putting a minus ('-') before a field name, reverts the sort order for this field. If not specified, a default sort field and sort order is used.
   * @returns org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_OrtRunSummary_ Success
   * @throws ApiError
   */
  public static getOrtRuns(
    repositoryId?: number,
    limit?: number,
    offset?: number,
    sort?: string
  ): CancelablePromise<org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_OrtRunSummary_> {
    return __request(OpenAPI, {
      method: 'GET',
      url: '/api/v1/repositories/{repositoryId}/runs',
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
   * Create an ORT run for a repository.
   * @param repositoryId The repository's ID.
   * @param requestBody
   * @returns OrtRun Success
   * @throws ApiError
   */
  public static postOrtRun(
    repositoryId?: number,
    requestBody?: CreateOrtRun
  ): CancelablePromise<OrtRun> {
    return __request(OpenAPI, {
      method: 'POST',
      url: '/api/v1/repositories/{repositoryId}/runs',
      path: {
        repositoryId: repositoryId,
      },
      body: requestBody,
      mediaType: 'application/json',
      errors: {
        401: `Invalid Token`,
      },
    });
  }

  /**
   * Get details of an ORT run of a repository.
   * @param repositoryId The repository's ID.
   * @param ortRunIndex The index of an ORT run.
   * @returns OrtRun Success
   * @throws ApiError
   */
  public static getOrtRunByIndex(
    repositoryId?: number,
    ortRunIndex?: number
  ): CancelablePromise<OrtRun> {
    return __request(OpenAPI, {
      method: 'GET',
      url: '/api/v1/repositories/{repositoryId}/runs/{ortRunIndex}',
      path: {
        repositoryId: repositoryId,
        ortRunIndex: ortRunIndex,
      },
      errors: {
        401: `Invalid Token`,
      },
    });
  }
}
