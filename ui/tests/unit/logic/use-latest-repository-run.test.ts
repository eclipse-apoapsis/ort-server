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

// @vitest-environment jsdom

import { renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { OrtRunSummary } from '@/api';
import { config } from '@/config';
import { useLatestRepositoryRun } from '@/hooks/use-latest-repository-run';
import { isCompiledByReactCompiler } from '../fixtures/react-compiler';

type RefetchInterval = (query: {
  state: {
    data?: {
      data: Array<{ finishedAt?: string | null }>;
    };
  };
}) => number | undefined;

const mocks = vi.hoisted(() => ({
  runs: [] as OrtRunSummary[],
  queryOptions: undefined as unknown,
}));

vi.mock('@tanstack/react-query', () => ({
  queryOptions: (queryOptions: unknown) => queryOptions,
  useSuspenseQuery: (queryOptions: unknown) => {
    mocks.queryOptions = queryOptions;
    return { data: { data: mocks.runs } };
  },
}));

const getRefetchInterval = () => {
  const refetchInterval = (
    mocks.queryOptions as { refetchInterval?: RefetchInterval }
  ).refetchInterval;

  expect(refetchInterval).toBeTypeOf('function');

  return refetchInterval as RefetchInterval;
};

const runSummary = (finishedAt?: string | null) =>
  ({ id: 1, finishedAt }) as OrtRunSummary;

const renderLatestRun = () => renderHook(() => useLatestRepositoryRun(42));

describe('useLatestRepositoryRun', () => {
  beforeEach(() => {
    mocks.runs = [];
    mocks.queryOptions = undefined;
  });

  it('is compiled by React Compiler', () => {
    expect(isCompiledByReactCompiler(useLatestRepositoryRun)).toBe(true);
  });

  it('returns the latest run', () => {
    const run = runSummary();
    mocks.runs = [run];

    expect(renderLatestRun().result.current).toBe(run);
  });

  it('returns undefined when the repository has no runs', () => {
    expect(renderLatestRun().result.current).toBeUndefined();
  });

  it('polls while the latest run is unfinished', () => {
    renderLatestRun();

    expect(
      getRefetchInterval()({ state: { data: { data: [runSummary()] } } })
    ).toBe(config.pollInterval);
  });

  it('stops polling after the latest run finishes', () => {
    renderLatestRun();

    expect(
      getRefetchInterval()({
        state: { data: { data: [runSummary('2026-01-01T00:00:00Z')] } },
      })
    ).toBeUndefined();
  });
});
