/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { CreateInfrastructureService } from '../models/CreateInfrastructureService';
import type { InfrastructureService } from '../models/InfrastructureService';
import type { org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_InfrastructureService_ } from '../models/org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_InfrastructureService_';
import type { UpdateInfrastructureService } from '../models/UpdateInfrastructureService';

import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';

export class InfrastructureServicesService {

    /**
     * List all infrastructure services of an organization.
     * @param organizationId The ID of an organization.
     * @param limit The maximum number of items to retrieve. If not specified at most 20 items are retrieved.
     * @param offset The offset of the first item in the result. Together with 'limit', this can be used to implement paging.
     * @param sort Comma-separated list of fields by which the result is sorted. The listed fields must be supported by the endpoint. Putting a minus ('-') before a field name, reverts the sort order for this field. If not specified, a default sort field and sort order is used.
     * @returns org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_InfrastructureService_ Success
     * @throws ApiError
     */
    public static getInfrastructureServicesByOrganizationId(
        organizationId?: number,
        limit?: number,
        offset?: number,
        sort?: string,
    ): CancelablePromise<org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_InfrastructureService_> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/organizations/{organizationId}/infrastructure-services',
            path: {
                'organizationId': organizationId,
            },
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
     * Create an infrastructure service for an organization.
     * @param requestBody
     * @returns InfrastructureService Success
     * @throws ApiError
     */
    public static postInfrastructureServiceForOrganization(
        requestBody?: CreateInfrastructureService,
    ): CancelablePromise<InfrastructureService> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/organizations/{organizationId}/infrastructure-services',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                401: `Invalid Token`,
            },
        });
    }

    /**
     * Delete an infrastructure service from an organization.
     * @param organizationId The organization's ID.
     * @param serviceName The name of the infrastructure service.
     * @returns void
     * @throws ApiError
     */
    public static deleteInfrastructureServiceForOrganizationIdAndName(
        organizationId?: number,
        serviceName?: string,
    ): CancelablePromise<void> {
        return __request(OpenAPI, {
            method: 'DELETE',
            url: '/api/v1/organizations/{organizationId}/infrastructure-services/{serviceName}',
            path: {
                'organizationId': organizationId,
                'serviceName': serviceName,
            },
            errors: {
                401: `Invalid Token`,
            },
        });
    }

    /**
     * Update an infrastructure service for an organization.
     * @param organizationId The organization's ID.
     * @param serviceName The name of the infrastructure service.
     * @param requestBody Set the values that should be updated. To delete a value, set it explicitly to null.
     * @returns InfrastructureService Success
     * @throws ApiError
     */
    public static patchInfrastructureServiceForOrganizationIdAndName(
        organizationId?: number,
        serviceName?: string,
        requestBody?: UpdateInfrastructureService,
    ): CancelablePromise<InfrastructureService> {
        return __request(OpenAPI, {
            method: 'PATCH',
            url: '/api/v1/organizations/{organizationId}/infrastructure-services/{serviceName}',
            path: {
                'organizationId': organizationId,
                'serviceName': serviceName,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                401: `Invalid Token`,
            },
        });
    }

}
