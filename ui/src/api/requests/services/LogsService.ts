/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';

export class LogsService {

    /**
     * Download an archive with selected logs of an ORT run.
     * @param runId The ID of the ORT run.
     * @param level The log level; can be one of 'DEBUG', 'INFO', 'WARN', 'ERROR' (ignoring case).Only logs of this level or higher are retrieved. Defaults to 'INFO' if missing.
     * @param steps Defines the run steps for which logs are to be retrieved. This is a comma-separated string with the following allowed steps: 'CONFIG', 'ANALYZER', 'ADVISOR', 'SCANNER', 'EVALUATOR', 'REPORTER' (ignoring case). If missing, the logs for all steps are retrieved.
     * @returns any Success. The response body contains a Zip archive with the selected log files.
     * @throws ApiError
     */
    public static getLogsByRunId(
        runId?: number,
        level?: string,
        steps?: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/runs/{runId}/logs',
            path: {
                'runId': runId,
            },
            query: {
                'level': level,
                'steps': steps,
            },
            errors: {
                400: `Invalid values have been provided for the log level or steps parameters.`,
                401: `Invalid Token`,
                404: `The ORT run does not exist.`,
            },
        });
    }

}
