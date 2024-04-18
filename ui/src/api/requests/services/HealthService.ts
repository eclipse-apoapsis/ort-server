/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { Liveness } from '../models/Liveness';

import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';

export class HealthService {

    /**
     * Get the health of the ORT server.
     * @returns Liveness Success
     * @throws ApiError
     */
    public static getLiveness(): CancelablePromise<Liveness> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/liveness',
        });
    }

}
