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

import { screen, waitFor, within } from '@testing-library/react';
import { Suspense } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { OrtRunSummary } from '@/api';
import { TooltipProvider } from '@/components/ui/tooltip';
import { RepositoryRunsTable } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/-components/repository-runs-table';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';

type DiffDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  baseRunIndex: number;
  comparedRunIndex: number;
};

const mocks = vi.hoisted(() => ({
  organization: vi.fn(),
  product: vi.fn(),
  repository: vi.fn(),
  run: vi.fn(),
  runs: vi.fn(),
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  getOrganization: mocks.organization,
  getProduct: mocks.product,
  getRepository: mocks.repository,
  getRepositoryRun: mocks.run,
  getRepositoryRuns: mocks.runs,
}));

// Poll the runs often, so that the tests do not have to wait for refreshed data.
vi.mock('@/config', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/config')>();

  return { config: { ...original.config, pollInterval: 50 } };
});

vi.mock('@/hooks/use-authorization', () => ({
  useRepositoryPermission: () => ({
    isAllowed: true,
    isPending: false,
    error: null,
  }),
}));

vi.mock('@/components/favorite-button', () => ({
  FavoriteButton: () => null,
}));

vi.mock('@/components/delete-dialog', () => ({
  DeleteDialog: () => null,
}));

vi.mock('@/components/ort-run-job-status', () => ({
  OrtRunJobStatus: () => null,
}));

vi.mock(
  '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/-components/run-item-counts',
  () => ({ RunItemCounts: () => null })
);

vi.mock(
  '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/-components/run-configuration-diff-dialog',
  () => ({
    RunConfigurationDiffDialog: ({
      open,
      onOpenChange,
      baseRunIndex,
      comparedRunIndex,
    }: DiffDialogProps) =>
      open ? (
        <div role='dialog'>
          Comparing run {baseRunIndex} with run {comparedRunIndex}
          <button type='button' onClick={() => onOpenChange(false)}>
            Close
          </button>
        </div>
      ) : null,
  })
);

const selectedLabel = 'Selected for comparison';
const selectBaseLabel = 'Select the base run for comparison';
const selectComparedLabel = 'Select the run to compare against the base run';

const createRun = (
  index: number,
  status: OrtRunSummary['status']
): OrtRunSummary => ({
  id: index + 100,
  index,
  organizationId: 1,
  productId: 2,
  repositoryId: 3,
  revision: 'main',
  createdAt: '2026-01-01T00:00:00Z',
  jobs: {},
  labels: {},
  status,
});

const runs = (...summaries: OrtRunSummary[]) => ({
  data: {
    data: summaries,
    pagination: { limit: 10, offset: 0, totalCount: summaries.length },
  },
});

const renderTable = () =>
  renderInteractiveWithRouter(
    <Suspense>
      <TooltipProvider>
        <RepositoryRunsTable
          orgId='1'
          productId='2'
          repoId='3'
          pageIndex={0}
          pageSize={10}
          search={{}}
        />
      </TooltipProvider>
    </Suspense>,
    {
      path: '/runs',
      routes: [{ path: '/runs' }],
      withQueryClient: true,
    }
  );

// Find the comparison toggle in the row of the run with the given index.
const findToggle = async (runIndex: number) => {
  const link = await screen.findByRole('link', { name: `${runIndex}` });

  return within(link.closest('tr')!).getByRole('button', {
    name: /comparison|compare/,
  });
};

describe('RepositoryRunsTable', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.organization.mockResolvedValue({ data: { id: 1, name: 'org' } });
    mocks.product.mockResolvedValue({
      data: { id: 2, organizationId: 1, name: 'product' },
    });
    mocks.repository.mockResolvedValue({
      data: {
        id: 3,
        organizationId: 1,
        productId: 2,
        type: 'GIT',
        url: 'https://example.com/repository.git',
      },
    });
    mocks.run.mockResolvedValue({ data: {} });
    mocks.runs.mockResolvedValue(
      runs(
        createRun(3, 'ACTIVE'),
        createRun(2, 'FINISHED'),
        createRun(1, 'FINISHED')
      )
    );
  });

  it('is compiled by React Compiler', () => {
    expect(RepositoryRunsTable.toString()).toContain(
      'react.memo_cache_sentinel'
    );
  });

  it('marks the base run and offers the other runs for comparison', async () => {
    const { user } = renderTable();

    for (const runIndex of [1, 2, 3]) {
      expect(await findToggle(runIndex)).toHaveAccessibleName(selectBaseLabel);
    }

    await user.click(await findToggle(2));

    await waitFor(async () =>
      expect(await findToggle(2)).toHaveAccessibleName(selectedLabel)
    );
    expect(await findToggle(2)).toHaveAttribute('aria-pressed', 'true');
    for (const runIndex of [1, 3]) {
      const toggle = await findToggle(runIndex);

      expect(toggle).toHaveAccessibleName(selectComparedLabel);
      expect(toggle).toHaveAttribute('aria-pressed', 'false');
    }
  });

  it('compares two selected runs and clears the selection afterwards', async () => {
    const { user } = renderTable();

    await user.click(await findToggle(1));
    await user.click(await findToggle(3));

    expect(await screen.findByRole('dialog')).toHaveTextContent(
      'Comparing run 1 with run 3'
    );
    for (const runIndex of [1, 3]) {
      expect(await findToggle(runIndex)).toHaveAttribute(
        'aria-pressed',
        'true'
      );
    }

    await user.click(screen.getByRole('button', { name: 'Close' }));

    await waitFor(() =>
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    );
    for (const runIndex of [1, 2, 3]) {
      const toggle = await findToggle(runIndex);

      expect(toggle).toHaveAccessibleName(selectBaseLabel);
      expect(toggle).toHaveAttribute('aria-pressed', 'false');
    }
  });

  it('shows polled data and keeps the selection', async () => {
    const { user } = renderTable();

    await user.click(await findToggle(2));
    expect(await screen.findByText('ACTIVE')).toBeVisible();

    mocks.runs.mockResolvedValue(
      runs(
        createRun(3, 'FAILED'),
        createRun(2, 'FINISHED'),
        createRun(1, 'FINISHED')
      )
    );

    expect(await screen.findByText('FAILED')).toBeVisible();
    expect(screen.queryByText('ACTIVE')).not.toBeInTheDocument();
    expect(await findToggle(2)).toHaveAccessibleName(selectedLabel);
    expect(await findToggle(2)).toHaveAttribute('aria-pressed', 'true');
    expect(await findToggle(3)).toHaveAccessibleName(selectComparedLabel);
  });
});
