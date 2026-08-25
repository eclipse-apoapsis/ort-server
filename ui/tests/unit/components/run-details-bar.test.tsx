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

import { screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { OrtRun } from '@/api';
import { RunDetailsBar } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/-components/run-details-bar';
import { createOrtRun } from '../fixtures/create-run';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';

const mocks = vi.hoisted(() => ({
  allRunIndexes: [1, 3, 5],
  canTriggerRun: true,
  ortRun: undefined as OrtRun | undefined,
}));

vi.mock('@tanstack/react-query', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@tanstack/react-query')>();

  return {
    ...actual,
    useSuspenseQuery: (options: { queryKey: string[] }) => ({
      data:
        options.queryKey[0] === 'repository-runs'
          ? mocks.allRunIndexes
          : mocks.ortRun,
    }),
  };
});

vi.mock('@/api/@tanstack/react-query.gen', () => ({
  getRepositoryRunsOptions: () => ({ queryKey: ['repository-runs'] }),
  getRepositoryRunOptions: () => ({ queryKey: ['repository-run'] }),
}));

vi.mock('@/hooks/use-authorization', () => ({
  useRepositoryPermission: () => ({ isAllowed: mocks.canTriggerRun }),
}));

vi.mock('@/components/favorite-button', () => ({
  RunFavoriteButton: () => null,
}));

vi.mock('@/components/ort-run-job-status', () => ({
  OrtRunJobStatus: () => null,
}));

vi.mock('@/components/run-duration', () => ({
  RunDuration: () => null,
}));

vi.mock('@/components/sha1-component', () => ({
  Sha1Component: () => null,
}));

vi.mock('@/components/timestamp-with-utc', () => ({
  TimestampWithUTC: () => null,
}));

const runsRoute =
  '/organizations/$orgId/products/$productId/repositories/$repoId/runs';
const runRoute = `${runsRoute}/$runIndex`;
const runPath = '/organizations/1/products/2/repositories/3/runs';

const renderBar = (runIndex: number, suffix = '/config?tab=jobs') => {
  mocks.ortRun = createOrtRun({
    createdAt: '2026-01-01T00:00:00Z',
    id: 42,
    index: runIndex,
    jobs: {},
    status: 'FINISHED',
  });

  return renderInteractiveWithRouter(<RunDetailsBar />, {
    path: `${runPath}/${runIndex}${suffix}`,
    routes: [
      { path: runsRoute },
      {
        path: runRoute,
        children: [{ path: 'config' }],
      },
      {
        path: '/organizations/$orgId/products/$productId/repositories/$repoId/create-run',
      },
    ],
  });
};

describe('RunDetailsBar', () => {
  beforeEach(() => {
    mocks.allRunIndexes = [1, 3, 5];
    mocks.canTriggerRun = true;
  });

  it('keeps the current page and search in previous and next links', async () => {
    const { container } = renderBar(3);

    expect(
      await screen.findByRole('link', { name: 'Previous' })
    ).toHaveAttribute('href', `${runPath}/1/config?tab=jobs`);
    expect(screen.getByRole('link', { name: 'All' })).toHaveAttribute(
      'href',
      runPath
    );
    expect(screen.getByRole('link', { name: 'Next' })).toHaveAttribute(
      'href',
      `${runPath}/5/config?tab=jobs`
    );
    expect(screen.getByRole('link', { name: 'Rerun' })).toHaveAttribute(
      'href',
      '/organizations/1/products/2/repositories/3/create-run?rerunIndex=3'
    );
    expect(container.querySelector('a button')).not.toBeInTheDocument();
  });

  it.each([
    { runIndex: 1, name: 'Previous' },
    { runIndex: 5, name: 'Next' },
  ])(
    'disables the $name link at the run boundary',
    async ({ runIndex, name }) => {
      renderBar(runIndex, '');

      expect((await screen.findByText(name)).closest('a')).toHaveAttribute(
        'aria-disabled',
        'true'
      );
    }
  );

  it('disables rerun without permission', async () => {
    mocks.canTriggerRun = false;

    renderBar(3, '');

    expect(await screen.findByRole('link', { name: 'Rerun' })).toHaveAttribute(
      'aria-disabled',
      'true'
    );
  });
});
