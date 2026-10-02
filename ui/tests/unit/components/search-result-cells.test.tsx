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
import { Suspense, type ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  SearchResultProductCell,
  SearchResultRepositoryCell,
} from '@/routes/organizations/$orgId/-components/search-result-cells';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';

const mocks = vi.hoisted(() => ({
  product: vi.fn(),
  repository: vi.fn(),
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  getProduct: mocks.product,
  getRepository: mocks.repository,
}));

const run = { organizationId: 1, productId: 2, repositoryId: 3 };

const renderCell = (cell: ReactNode) =>
  renderInteractiveWithRouter(<Suspense>{cell}</Suspense>, {
    path: '/',
    withQueryClient: true,
  });

describe('search result cells', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.product.mockResolvedValue({ data: { name: 'Product' } });
    mocks.repository.mockResolvedValue({
      data: { url: 'https://example.com/repository.git' },
    });
  });

  it('links the product name to the product', async () => {
    renderCell(<SearchResultProductCell run={run} />);

    expect(
      await screen.findByRole('link', { name: 'Product' })
    ).toHaveAttribute('href', '/organizations/1/products/2');
    expect(mocks.product.mock.calls[0]?.[0]).toMatchObject({
      path: { productId: 2 },
    });
  });

  it('links the repository URL to the runs of the repository', async () => {
    renderCell(<SearchResultRepositoryCell run={run} />);

    expect(
      await screen.findByRole('link', {
        name: 'https://example.com/repository.git',
      })
    ).toHaveAttribute(
      'href',
      '/organizations/1/products/2/repositories/3/runs'
    );
    expect(mocks.repository.mock.calls[0]?.[0]).toMatchObject({
      path: { repositoryId: 3 },
    });
  });
});
