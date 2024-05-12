/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { CreateProduct } from '../models/CreateProduct';
import type { org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Product_ } from '../models/org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Product_';
import type { Product } from '../models/Product';
import type { UpdateProduct } from '../models/UpdateProduct';

import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';

export class ProductsService {
  /**
   * Get all products of an organization.
   * @param organizationId The organization's ID.
   * @param limit The maximum number of items to retrieve. If not specified at most 20 items are retrieved.
   * @param offset The offset of the first item in the result. Together with 'limit', this can be used to implement paging.
   * @param sort Comma-separated list of fields by which the result is sorted. The listed fields must be supported by the endpoint. Putting a minus ('-') before a field name, reverts the sort order for this field. If not specified, a default sort field and sort order is used.
   * @returns org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Product_ Success
   * @throws ApiError
   */
  public static getOrganizationProducts(
    organizationId?: number,
    limit?: number,
    offset?: number,
    sort?: string
  ): CancelablePromise<org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Product_> {
    return __request(OpenAPI, {
      method: 'GET',
      url: '/api/v1/organizations/{organizationId}/products',
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
   * Create a product for an organization.
   * @param organizationId The organization's ID.
   * @param requestBody
   * @returns Product Success
   * @throws ApiError
   */
  public static postProduct(
    organizationId?: number,
    requestBody?: CreateProduct
  ): CancelablePromise<Product> {
    return __request(OpenAPI, {
      method: 'POST',
      url: '/api/v1/organizations/{organizationId}/products',
      path: {
        organizationId: organizationId,
      },
      body: requestBody,
      mediaType: 'application/json',
      errors: {
        401: `Invalid Token`,
      },
    });
  }

  /**
   * Get details of a product.
   * @param productId The product's ID.
   * @returns Product Success
   * @throws ApiError
   */
  public static getProductById(productId?: number): CancelablePromise<Product> {
    return __request(OpenAPI, {
      method: 'GET',
      url: '/api/v1/products/{productId}',
      path: {
        productId: productId,
      },
      errors: {
        401: `Invalid Token`,
      },
    });
  }

  /**
   * Delete a product.
   * @param productId The product's ID.
   * @returns void
   * @throws ApiError
   */
  public static deleteProductById(productId?: number): CancelablePromise<void> {
    return __request(OpenAPI, {
      method: 'DELETE',
      url: '/api/v1/products/{productId}',
      path: {
        productId: productId,
      },
      errors: {
        401: `Invalid Token`,
      },
    });
  }

  /**
   * Update a product.
   * @param productId The product's ID.
   * @param requestBody Set the values that should be updated. To delete a value, set it explicitly to null.
   * @returns Product Success
   * @throws ApiError
   */
  public static patchProductById(
    productId?: number,
    requestBody?: UpdateProduct
  ): CancelablePromise<Product> {
    return __request(OpenAPI, {
      method: 'PATCH',
      url: '/api/v1/products/{productId}',
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
}
