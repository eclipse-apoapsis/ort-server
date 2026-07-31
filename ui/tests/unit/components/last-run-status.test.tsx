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
import { LastRunStatus } from '@/routes/organizations/$orgId/products/$productId/-components/last-run-status';

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

const renderLastRunStatus = () =>
  renderToStaticMarkup(<LastRunStatus repoId={42} />);

describe('LastRunStatus', () => {
  beforeEach(() => {
    mocks.latestRun = undefined;
    mocks.suspend = false;
  });

  it('renders the latest run status', () => {
    mocks.latestRun = { status: 'FINISHED' } as OrtRunSummary;

    const markup = renderLastRunStatus();

    expect(markup).toContain('data-slot="badge"');
    expect(markup).toContain('FINISHED');
  });

  it('renders a skeleton while the latest run is loading', () => {
    mocks.suspend = true;

    const markup = renderLastRunStatus();

    expect(markup).toContain('data-slot="skeleton"');
    expect(markup).toContain('h-5 w-16 rounded-md');
    expect(markup).toContain('Loading data...');
    expect(markup).not.toContain('data-slot="badge"');
  });

  it('reserves space when the repository has no runs', () => {
    const markup = renderLastRunStatus();

    expect(markup).toContain('class="min-h-5"');
    expect(markup).not.toContain('data-slot="badge"');
    expect(markup).not.toContain('data-slot="skeleton"');
  });
});
