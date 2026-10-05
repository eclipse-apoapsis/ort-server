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

import { defaultParseSearch } from '@tanstack/react-router';
import { act, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type {
  GetProductVulnerabilitiesData,
  VulnerabilityWithStats,
} from '@/api';
import { TooltipProvider } from '@/components/ui/tooltip';
import { Route } from '@/routes/organizations/$orgId/products/$productId/vulnerabilities/index';
import { packageIdTypeSchema } from '@/schemas';
import { useUserSettingsStore } from '@/store/user-settings.store';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';

const mocks = vi.hoisted(() => ({
  advisors: vi.fn(),
  vulnerabilities: vi.fn(),
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  getProductVulnerabilityAdvisors: mocks.advisors,
  getProductVulnerabilities: mocks.vulnerabilities,
}));

const ProductVulnerabilitiesComponent = Route.options.component!;

type VulnerabilitiesQuery = NonNullable<GetProductVulnerabilitiesData['query']>;

const purlOf = (name: string) => `pkg:maven/com.example/${name}@1.0`;

// Serve three vulnerabilities per page, named after the query and their position. The total
// query, with a limit of one, always reports 25 vulnerabilities.
const getVulnerabilitiesPage = (query: VulnerabilitiesQuery = {}) => {
  const tag = query.rating ? 'filtered' : 'vuln';
  const offset = query.offset ?? 0;
  const data: VulnerabilityWithStats[] = [1, 2, 3].map((position) => {
    const name = `${tag}-${offset + position}`;

    return {
      identifier: {
        type: 'Maven',
        namespace: 'com.example',
        name,
        version: '1.0',
      },
      ortRunIds: [1],
      purl: purlOf(name),
      rating: 'HIGH',
      repositoriesCount: 2,
      vulnerability: {
        externalId: `CVE-${name}`,
        summary: `Summary of ${name}`,
        references: [],
      },
    };
  });

  return {
    data: {
      data,
      pagination: {
        limit: query.limit ?? 10,
        offset,
        totalCount: query.limit !== 1 && query.rating ? 13 : 25,
      },
    },
  };
};

const renderVulnerabilities = () =>
  renderInteractiveWithRouter(
    <TooltipProvider>
      <ProductVulnerabilitiesComponent />
    </TooltipProvider>,
    {
      path: '/organizations/1/products/2/vulnerabilities',
      routes: [
        { path: '/organizations/$orgId/products/$productId/vulnerabilities/' },
        { path: '/organizations/$orgId/products/$productId/search-package' },
      ],
      withQueryClient: true,
    }
  );

const findVulnerability = (name: string) =>
  screen.findByText(`Summary of ${name}`);

// The query for a page of vulnerabilities, as opposed to the query for the total count.
const lastPageQuery = () =>
  mocks.vulnerabilities.mock.calls
    .map((call) => call[0].query as VulnerabilitiesQuery)
    .filter((query) => query.limit !== 1)
    .at(-1);

beforeEach(() => {
  vi.clearAllMocks();
  useUserSettingsStore.setState({
    packageIdType: packageIdTypeSchema.enum.PURL,
  });
  mocks.advisors.mockResolvedValue({ data: ['OSV'] });
  mocks.vulnerabilities.mockImplementation(
    async ({ query }: { query?: VulnerabilitiesQuery }) =>
      getVulnerabilitiesPage(query)
  );
});

describe('product vulnerabilities table', () => {
  it('is compiled by React Compiler', () => {
    expect(ProductVulnerabilitiesComponent.toString()).toContain(
      'react.memo_cache_sentinel'
    );
  });

  it('applies filters and sorting from the URL', async () => {
    const { container, router } = renderVulnerabilities();

    expect(await findVulnerability('vuln-1')).toBeVisible();
    expect(lastPageQuery()?.sort).toBeUndefined();

    await act(() =>
      router.navigate({
        to: '.',
        search: {
          rating: ['HIGH'],
          sortBy: [{ id: 'rating', desc: true }],
        },
      })
    );

    expect(await findVulnerability('filtered-1')).toBeVisible();
    expect(screen.queryByText('Summary of vuln-1')).not.toBeInTheDocument();
    expect(lastPageQuery()).toMatchObject({
      rating: 'HIGH',
      sort: '-rating',
    });
    expect(container).toHaveTextContent(
      'Vulnerabilities (25 in total, 13 matching filters)'
    );
  });

  it('links the affected repositories to the package search of the product', async () => {
    renderVulnerabilities();

    expect(await findVulnerability('vuln-1')).toBeVisible();

    const link = screen.getAllByRole('link', { name: /repositories/ })[0]!;
    const target = new URL(link.getAttribute('href')!, 'http://localhost');

    expect(target.pathname).toBe('/organizations/1/products/2/search-package');
    expect(defaultParseSearch(target.search)).toEqual({
      pkgId: purlOf('vuln-1'),
    });
  });
});
