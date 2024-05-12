/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';

export class ReportsService {
  /**
   * Download a report of an ORT run using a token. This endpoint does not require authentication.
   * @param runId The ID of the ORT run.
   * @param token The token providing access to the report file to be downloaded.
   * @returns string Success. The response body contains the requested report file.
   * @throws ApiError
   */
  public static getReportByRunIdAndToken(
    runId?: number,
    token?: string
  ): CancelablePromise<string> {
    return __request(OpenAPI, {
      method: 'GET',
      url: '/api/v1/runs/{runId}/downloads/report/{token}',
      path: {
        runId: runId,
        token: token,
      },
      responseHeader: 'Content-Type',
      errors: {
        404: `The provided token could not be resolved or has expired.`,
      },
    });
  }

  /**
   * Download a report of an ORT run.
   * @param runId The ID of the ORT run.
   * @param fileName The name of the report file to be downloaded.
   * @returns string Success. The response body contains the requested report file.
   * @throws ApiError
   */
  public static getReportByRunIdAndFileName(
    runId?: number,
    fileName?: string
  ): CancelablePromise<string> {
    return __request(OpenAPI, {
      method: 'GET',
      url: '/api/v1/runs/{runId}/reporter/{fileName}',
      path: {
        runId: runId,
        fileName: fileName,
      },
      responseHeader: 'Content-Type',
      errors: {
        401: `Invalid Token`,
        404: `The requested report file or the ORT run could not be resolved.`,
      },
    });
  }
}
