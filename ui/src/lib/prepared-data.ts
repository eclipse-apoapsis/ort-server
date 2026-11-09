/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

/** The delay before repeating a request if the server did not send a usable `Retry-After` header. */
const DEFAULT_RETRY_AFTER_MS = 1_000;

/** The maximum number of requests that are made while the server is preparing the data. */
const MAX_PREPARING_REQUESTS = 150;

/** The maximum number of times a request is repeated after an error. */
const MAX_ERROR_RETRIES = 3;

/** An error that indicates that the server did not finish preparing the requested data in time. */
export class DataPreparingError extends Error {
  constructor() {
    super('The data is still being prepared.');
    this.name = 'DataPreparingError';
  }
}

type PreparableResponse = {
  status: number;
  headers: Record<string, unknown>;
};

/**
 * Make a request with `request` and repeat it as long as the server answers with `202 Accepted`, which means that it
 * is still preparing the requested data. Before a request is repeated, wait for the delay requested by the server in
 * the `Retry-After` header. Throw a `DataPreparingError` if the data is still not ready after `MAX_PREPARING_REQUESTS`
 * requests, and stop waiting if the `signal` is aborted.
 *
 * The requests are repeated here instead of using the retries of TanStack Query, so that its retries are only used for
 * actual errors.
 */
export async function requestPreparedData<T extends PreparableResponse>(
  request: () => Promise<T>,
  signal?: AbortSignal
): Promise<T> {
  for (let requests = 1; ; requests++) {
    const response = await request();
    if (response.status !== 202) return response;
    if (requests >= MAX_PREPARING_REQUESTS) throw new DataPreparingError();

    await delay(getRetryAfterMs(response), signal);
  }
}

/** Return the delay requested by the `Retry-After` header of the `response` in milliseconds. */
function getRetryAfterMs(response: PreparableResponse): number {
  const retryAfterSeconds = Number(response.headers['retry-after']);

  return Number.isFinite(retryAfterSeconds) && retryAfterSeconds > 0
    ? retryAfterSeconds * 1000
    : DEFAULT_RETRY_AFTER_MS;
}

/** Wait for `ms` milliseconds, or until the `signal` is aborted. */
function delay(ms: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(signal.reason);
      return;
    }

    const onAbort = () => {
      clearTimeout(timeout);
      reject(signal?.reason);
    };

    const timeout = setTimeout(() => {
      signal?.removeEventListener('abort', onAbort);
      resolve();
    }, ms);

    signal?.addEventListener('abort', onAbort, { once: true });
  });
}

/**
 * The retry options for queries of data that may need to be prepared by the server: Errors are retried a few times
 * with the default backoff of TanStack Query, but not if the server did not finish preparing the data in time, because
 * the data was already requested repeatedly then.
 */
export const preparedDataRetryOptions = {
  retry: (failureCount: number, error: unknown) =>
    !(error instanceof DataPreparingError) && failureCount < MAX_ERROR_RETRIES,
};
