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
import { afterEach, beforeEach, expect, it, vi } from 'vitest';

import type { OrtRunSummary } from '@/api';
import { Siblings } from '@/components/siblings';
import { TooltipProvider } from '@/components/ui/tooltip';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';
import { pageOf } from '../fixtures/table';

const mocks = vi.hoisted(() => ({
  getRepositoryRuns: vi.fn(),
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  ...mocks,
}));

beforeEach(() => {
  // The dropdown asks for its next page when its end scrolls into view.
  vi.stubGlobal(
    'IntersectionObserver',
    class {
      observe() {}
      disconnect() {}
    }
  );
});

afterEach(() => {
  vi.unstubAllGlobals();
});

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

it('shows the latest status of the runs each time the run dropdown opens', async () => {
  // The same endpoint answers the count behind the dropdown and the list in it.
  mocks.getRepositoryRuns.mockResolvedValue(
    pageOf([createRun(2, 'ACTIVE'), createRun(1, 'FAILED')])
  );

  const { user } = renderInteractiveWithRouter(
    <TooltipProvider>
      <Siblings entity='run' />
    </TooltipProvider>,
    {
      path: '/organizations/1/products/2/repositories/3/runs/2',
      routes: [
        {
          path: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex',
        },
      ],
      withQueryClient: true,
      context: { breadcrumbs: { run: 'Run 2' } },
    }
  );

  const trigger = await screen.findByRole('button', { name: 'Run 2' });
  await user.click(trigger);
  expect(await screen.findByText('ACTIVE')).toBeInTheDocument();
  await user.keyboard('{Escape}');

  // The server finishes the run while the dropdown is closed, which no action in the UI announces.
  mocks.getRepositoryRuns.mockResolvedValue(
    pageOf([createRun(2, 'FINISHED'), createRun(1, 'FAILED')])
  );

  await user.click(trigger);
  expect(await screen.findByText('FINISHED')).toBeInTheDocument();
  expect(screen.queryByText('ACTIVE')).not.toBeInTheDocument();
});
