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

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  DataPreparingError,
  preparedDataRetryOptions,
  requestPreparedData,
} from '@/lib/prepared-data';

const preparing = (retryAfter?: string) => ({
  status: 202,
  headers: retryAfter === undefined ? {} : { 'retry-after': retryAfter },
});

const ready = { status: 200, headers: {}, data: 'data' };

describe('requestPreparedData', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('returns the response if the data is ready', async () => {
    const request = vi.fn().mockResolvedValue(ready);

    await expect(requestPreparedData(request)).resolves.toBe(ready);
    expect(request).toHaveBeenCalledTimes(1);
  });

  it('repeats the request after the delay requested by the server', async () => {
    const request = vi
      .fn()
      .mockResolvedValueOnce(preparing('2'))
      .mockResolvedValueOnce(ready);

    const result = requestPreparedData(request);

    await vi.advanceTimersByTimeAsync(1_999);
    expect(request).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(1);
    await expect(result).resolves.toBe(ready);
    expect(request).toHaveBeenCalledTimes(2);
  });

  it('repeats the request after a default delay without a usable Retry-After header', async () => {
    const request = vi
      .fn()
      .mockResolvedValueOnce(preparing())
      .mockResolvedValueOnce(preparing('invalid'))
      .mockResolvedValueOnce(ready);

    const result = requestPreparedData(request);

    await vi.advanceTimersByTimeAsync(2_000);
    await expect(result).resolves.toBe(ready);
    expect(request).toHaveBeenCalledTimes(3);
  });

  it('throws a DataPreparingError if the data is not ready in time', async () => {
    const request = vi.fn().mockResolvedValue(preparing('1'));

    const result = requestPreparedData(request);
    const assertion = expect(result).rejects.toBeInstanceOf(DataPreparingError);

    await vi.runAllTimersAsync();
    await assertion;
    expect(request).toHaveBeenCalledTimes(150);
  });

  it('stops waiting if the signal is aborted', async () => {
    const request = vi.fn().mockResolvedValue(preparing('10'));
    const controller = new AbortController();

    const result = requestPreparedData(request, controller.signal);
    const assertion = expect(result).rejects.toBe('aborted');

    await vi.advanceTimersByTimeAsync(0);
    controller.abort('aborted');

    await assertion;
    expect(request).toHaveBeenCalledTimes(1);
  });
});

describe('preparedDataRetryOptions', () => {
  it('retries errors a few times', () => {
    const error = new Error('Network error');

    expect(preparedDataRetryOptions.retry(0, error)).toBe(true);
    expect(preparedDataRetryOptions.retry(2, error)).toBe(true);
    expect(preparedDataRetryOptions.retry(3, error)).toBe(false);
  });

  it('does not retry if the data was not prepared in time', () => {
    expect(preparedDataRetryOptions.retry(0, new DataPreparingError())).toBe(
      false
    );
  });
});
