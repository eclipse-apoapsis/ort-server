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

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import type { ComponentType } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { Product } from '@/api';
import {
  ProductJobStatusCell,
  ProductLastRunDateCell,
  ProductRunStatusCell,
  ProductTotalRunsCell,
} from '@/routes/organizations/$orgId/-components/organization-product-table';

const mocks = vi.hoisted(() => ({
  repositories: vi.fn(),
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  getProductRepositories: mocks.repositories,
}));

vi.mock(
  '@/routes/organizations/$orgId/products/$productId/-components/total-runs',
  () => ({
    TotalRuns: ({ repoId }: { repoId: number }) => (
      <span>Total runs of {repoId}</span>
    ),
  })
);

vi.mock(
  '@/routes/organizations/$orgId/products/$productId/-components/last-run-status',
  () => ({
    LastRunStatus: ({ repoId }: { repoId: number }) => (
      <span>Run status of {repoId}</span>
    ),
  })
);

vi.mock(
  '@/routes/organizations/$orgId/products/$productId/-components/last-run-date',
  () => ({
    LastRunDate: ({ repoId }: { repoId: number }) => (
      <span>Run date of {repoId}</span>
    ),
  })
);

vi.mock(
  '@/routes/organizations/$orgId/products/$productId/-components/last-job-status',
  () => ({
    LastJobStatus: ({ repoId }: { repoId: number }) => (
      <span>Job status of {repoId}</span>
    ),
  })
);

vi.mock(
  '@/routes/organizations/$orgId/-components/product-item-counts',
  () => ({
    ProductItemCounts: ({ productId }: { productId: number }) => (
      <span>Item counts of {productId}</span>
    ),
  })
);

type CellComponent = ComponentType<{ product: Product }>;

const product: Product = { id: 2, organizationId: 1, name: 'product' };

const repositories = (totalCount: number) => ({
  data: {
    data: [{ id: 7 }],
    pagination: { limit: 1, offset: 0, totalCount },
  },
});

const renderCell = (Cell: CellComponent) => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <Cell product={product} />
    </QueryClientProvider>
  );
};

const cells: {
  name: string;
  Cell: CellComponent;
  oneRepository: string[];
  severalRepositories: string[];
}[] = [
  {
    name: 'ProductTotalRunsCell',
    Cell: ProductTotalRunsCell,
    oneRepository: ['Total runs of 7'],
    severalRepositories: ['-'],
  },
  {
    name: 'ProductRunStatusCell',
    Cell: ProductRunStatusCell,
    oneRepository: ['Run status of 7', 'Item counts of 2'],
    severalRepositories: ['Contains 3 repositories', 'Item counts of 2'],
  },
  {
    name: 'ProductLastRunDateCell',
    Cell: ProductLastRunDateCell,
    oneRepository: ['Run date of 7'],
    severalRepositories: [],
  },
  {
    name: 'ProductJobStatusCell',
    Cell: ProductJobStatusCell,
    oneRepository: ['Job status of 7'],
    severalRepositories: [],
  },
];

describe.each(cells)(
  '$name',
  ({ Cell, oneRepository, severalRepositories }) => {
    beforeEach(() => {
      vi.clearAllMocks();
    });

    it('shows a spinner while the repositories are loading', () => {
      mocks.repositories.mockReturnValue(new Promise(() => undefined));

      renderCell(Cell);

      expect(screen.getByText('Loading...')).toBeInTheDocument();
    });

    it('shows an error when the repositories cannot be loaded', async () => {
      mocks.repositories.mockRejectedValue(new Error('failed'));

      renderCell(Cell);

      expect(await screen.findByText('Error loading data.')).toBeVisible();
    });

    it('shows the details of the only repository', async () => {
      mocks.repositories.mockResolvedValue(repositories(1));

      const { container } = renderCell(Cell);

      for (const text of oneRepository) {
        expect(await screen.findByText(text)).toBeVisible();
      }
      expect(container).toHaveTextContent(
        new RegExp(`^${oneRepository.join('')}$`)
      );
      expect(mocks.repositories.mock.calls[0]?.[0]).toMatchObject({
        path: { productId: 2 },
        query: { limit: 1 },
      });
    });

    it('shows no repository details for several repositories', async () => {
      mocks.repositories.mockResolvedValue(repositories(3));

      const { container } = renderCell(Cell);

      for (const text of severalRepositories) {
        expect(await screen.findByText(text)).toBeVisible();
      }
      await vi.waitFor(() =>
        expect(container).toHaveTextContent(
          new RegExp(`^${severalRepositories.join('')}$`)
        )
      );
    });
  }
);
