/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { CreateOrganization } from '../models/CreateOrganization';
import type { org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Organization_ } from '../models/org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Organization_';
import type { Organization } from '../models/Organization';
import type { UpdateOrganization } from '../models/UpdateOrganization';

import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';

export class OrganizationsService {

    /**
     * Get all organizations.
     * @param limit The maximum number of items to retrieve. If not specified at most 20 items are retrieved.
     * @param offset The offset of the first item in the result. Together with 'limit', this can be used to implement paging.
     * @param sort Comma-separated list of fields by which the result is sorted. The listed fields must be supported by the endpoint. Putting a minus ('-') before a field name, reverts the sort order for this field. If not specified, a default sort field and sort order is used.
     * @returns org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Organization_ Success
     * @throws ApiError
     */
    public static getOrganizations(
        limit?: number,
        offset?: number,
        sort?: string,
    ): CancelablePromise<org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Organization_> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/organizations',
            query: {
                'limit': limit,
                'offset': offset,
                'sort': sort,
            },
            errors: {
                401: `Invalid Token`,
            },
        });
    }

    /**
     * Create an organization.
     * @param requestBody
     * @returns Organization Success
     * @throws ApiError
     */
    public static postOrganizations(
        requestBody?: CreateOrganization,
    ): CancelablePromise<Organization> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/organizations',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                401: `Invalid Token`,
            },
        });
    }

    /**
     * Get details of an organization.
     * @param organizationId The organization's ID.
     * @returns Organization Success
     * @throws ApiError
     */
    public static getOrganizationById(
        organizationId?: number,
    ): CancelablePromise<Organization> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/organizations/{organizationId}',
            path: {
                'organizationId': organizationId,
            },
            errors: {
                401: `Invalid Token`,
            },
        });
    }

    /**
     * Delete an organization.
     * @param organizationId The organization's ID.
     * @returns void
     * @throws ApiError
     */
    public static deleteOrganizationById(
        organizationId?: number,
    ): CancelablePromise<void> {
        return __request(OpenAPI, {
            method: 'DELETE',
            url: '/api/v1/organizations/{organizationId}',
            path: {
                'organizationId': organizationId,
            },
            errors: {
                401: `Invalid Token`,
            },
        });
    }

    /**
     * Update an organization.
     * @param organizationId The organization's ID.
     * @param requestBody Set the values that should be updated. To delete a value, set it explicitly to null.
     * @returns Organization Success
     * @throws ApiError
     */
    public static patchOrganizationById(
        organizationId?: number,
        requestBody?: UpdateOrganization,
    ): CancelablePromise<Organization> {
        return __request(OpenAPI, {
            method: 'PATCH',
            url: '/api/v1/organizations/{organizationId}',
            path: {
                'organizationId': organizationId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                401: `Invalid Token`,
            },
        });
    }

}
