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

import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { OrtRunSummary } from '@/api';
import { LastRunDate } from '@/routes/organizations/$orgId/products/$productId/-components/last-run-date';

const mocks = vi.hoisted(() => ({
  latestRun: undefined as OrtRunSummary | undefined,
  suspend: false,
  pendingPromise: new Promise<never>(() => undefined),
}));

vi.mock('@/hooks/use-latest-repository-run', () => ({
  useLatestRepositoryRun: () => {
    if (mocks.suspend) throw mocks.pendingPromise;

    return mocks.latestRun;
  },
}));

vi.mock('@/components/timestamp-with-utc', () => ({
  TimestampWithUTC: ({
    timestamp,
    className,
  }: {
    timestamp: string;
    className?: string;
  }) => <span className={className}>{timestamp}</span>,
}));

const renderLastRunDate = () =>
  renderToStaticMarkup(<LastRunDate repoId={42} />);

describe('LastRunDate', () => {
  beforeEach(() => {
    mocks.latestRun = undefined;
    mocks.suspend = false;
  });

  it('renders the finish date and user in a stable-height container', () => {
    mocks.latestRun = {
      finishedAt: '2026-07-31T08:00:00Z',
      createdAt: '2026-07-31T07:00:00Z',
      userDisplayName: {
        fullName: 'Example User',
        username: 'example',
      },
    } as OrtRunSummary;

    const markup = renderLastRunDate();

    expect(markup).toContain('flex min-h-10 flex-col items-start');
    expect(markup).toContain('2026-07-31T08:00:00Z');
    expect(markup).toContain('Example User');
  });

  it('reserves two lines when the run has no user', () => {
    mocks.latestRun = {
      finishedAt: null,
      createdAt: '2026-07-31T07:00:00Z',
    } as OrtRunSummary;

    const markup = renderLastRunDate();

    expect(markup).toContain('flex min-h-10 flex-col items-start');
    expect(markup).toContain('class="italic"');
    expect(markup).toContain('2026-07-31T07:00:00Z');
  });

  it('renders two skeleton lines while the latest run is loading', () => {
    mocks.suspend = true;

    const markup = renderLastRunDate();

    expect(markup).toContain('flex min-h-10 flex-col gap-1');
    expect(markup.match(/data-slot="skeleton"/g)).toHaveLength(2);
    expect(markup).toContain('h-4 w-32');
    expect(markup).toContain('h-4 w-20');
    expect(markup).toContain('Loading data...');
  });

  it('reserves space when the repository has no runs', () => {
    const markup = renderLastRunDate();

    expect(markup).toContain('class="min-h-10"');
    expect(markup).not.toContain('data-slot="skeleton"');
  });
});
