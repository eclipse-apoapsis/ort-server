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
import { LastJobStatus } from '@/routes/organizations/$orgId/products/$productId/-components/last-job-status';

type OrtRunJobStatusProps = {
  jobs: OrtRunSummary['jobs'];
  orgId: string;
  productId: string;
  repoId: string;
  runIndex: string;
};

const mocks = vi.hoisted(() => ({
  latestRun: undefined as OrtRunSummary | undefined,
  suspend: false,
  jobStatusProps: undefined as OrtRunJobStatusProps | undefined,
  pendingPromise: new Promise<never>(() => undefined),
}));

vi.mock('@/hooks/use-latest-repository-run', () => ({
  useLatestRepositoryRun: () => {
    if (mocks.suspend) throw mocks.pendingPromise;

    return mocks.latestRun;
  },
}));

vi.mock('@/components/ort-run-job-status', () => ({
  OrtRunJobStatus: (props: OrtRunJobStatusProps) => {
    mocks.jobStatusProps = props;
    return <div data-slot='job-status' />;
  },
}));

const renderLastJobStatus = () =>
  renderToStaticMarkup(<LastJobStatus repoId={42} />);

describe('LastJobStatus', () => {
  beforeEach(() => {
    mocks.latestRun = undefined;
    mocks.suspend = false;
    mocks.jobStatusProps = undefined;
  });

  it('renders the latest run job statuses and links', () => {
    const jobs = {
      analyzer: { status: 'FINISHED' },
      advisor: { status: 'RUNNING' },
    } as OrtRunSummary['jobs'];

    mocks.latestRun = {
      jobs,
      organizationId: 1,
      productId: 2,
      repositoryId: 3,
      index: 7,
    } as OrtRunSummary;

    const markup = renderLastJobStatus();

    expect(markup).toContain('class="min-h-3"');
    expect(markup).toContain('data-slot="job-status"');
    expect(mocks.jobStatusProps).toEqual({
      jobs,
      orgId: '1',
      productId: '2',
      repoId: '3',
      runIndex: '7',
    });
  });

  it('renders five skeleton dots while the latest run is loading', () => {
    mocks.suspend = true;

    const markup = renderLastJobStatus();

    expect(markup).toContain('flex min-h-3 items-center space-x-1');
    expect(markup.match(/data-slot="skeleton"/g)).toHaveLength(5);
    expect(markup.match(/h-3 w-3 rounded-full/g)).toHaveLength(5);
    expect(markup).toContain('Loading data...');
    expect(mocks.jobStatusProps).toBeUndefined();
  });

  it('reserves space when the repository has no runs', () => {
    const markup = renderLastJobStatus();

    expect(markup).toContain('class="min-h-3"');
    expect(markup).not.toContain('data-slot="job-status"');
    expect(markup).not.toContain('data-slot="skeleton"');
    expect(mocks.jobStatusProps).toBeUndefined();
  });
});
