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
import { act, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type {
  GetOrganizationVulnerabilitiesData,
  VulnerabilityWithStats,
} from '@/api';
import { TooltipProvider } from '@/components/ui/tooltip';
import { identifierToString } from '@/helpers/identifier-conversion';
import { Route } from '@/routes/organizations/$orgId/vulnerabilities/index';
import { packageIdTypeSchema } from '@/schemas';
import { useUserSettingsStore } from '@/store/user-settings.store';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';

const mocks = vi.hoisted(() => ({
  advisors: vi.fn(),
  vulnerabilities: vi.fn(),
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  getOrganizationVulnerabilityAdvisors: mocks.advisors,
  getOrganizationVulnerabilities: mocks.vulnerabilities,
}));

vi.mock('@/components/charts/vulnerability-metrics', () => ({
  VulnerabilityMetrics: () => null,
}));

const OrganizationVulnerabilitiesComponent = Route.options.component!;

type VulnerabilitiesQuery = NonNullable<
  GetOrganizationVulnerabilitiesData['query']
>;

type VulnerabilitiesResponse = {
  data: {
    data: VulnerabilityWithStats[];
    pagination: { limit: number; offset: number; totalCount: number };
  };
};

const identifierOf = (name: string) => ({
  type: 'Maven',
  namespace: 'com.example',
  name,
  version: '1.0',
});

const purlOf = (name: string) => `pkg:maven/com.example/${name}@1.0`;

// The first part of the vulnerability names, which tells the query the vulnerabilities come from.
let nameTag = 'vuln';

// Serve three vulnerabilities per page, named after the query and their position. The total
// query, with a limit of one, always reports 25 vulnerabilities.
const getVulnerabilitiesPage = (
  query: VulnerabilitiesQuery = {}
): VulnerabilitiesResponse => {
  const filtered = Boolean(
    query.rating || query.externalId || query.identifier || query.purl
  );
  const tag = filtered ? 'filtered' : query.sort ? 'sorted' : nameTag;
  const offset = query.offset ?? 0;
  const data: VulnerabilityWithStats[] = [1, 2, 3].map((position) => {
    const name = `${tag}-${offset + position}`;

    return {
      identifier: identifierOf(name),
      ortRunIds: [1],
      purl: purlOf(name),
      rating: 'HIGH',
      repositoriesCount: 2,
      vulnerability: {
        externalId: `CVE-${name}`,
        summary: `Summary of ${name}`,
        description: `Description of ${name}`,
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
        totalCount: query.limit !== 1 && filtered ? 13 : 25,
      },
    },
  };
};

const renderVulnerabilities = (search = '') =>
  renderInteractiveWithRouter(
    <TooltipProvider>
      <OrganizationVulnerabilitiesComponent />
    </TooltipProvider>,
    {
      path: `/organizations/1/vulnerabilities${search}`,
      routes: [
        { path: '/organizations/$orgId/vulnerabilities/' },
        { path: '/organizations/$orgId/search-package' },
      ],
      withQueryClient: true,
    }
  );

const findVulnerability = (name: string) =>
  screen.findByText(`Summary of ${name}`);

// The button that expands and collapses the row of the given vulnerability.
const getDetailsButton = (name: string) =>
  within(screen.getByText(`Summary of ${name}`).closest('tr')!).getAllByRole(
    'button'
  )[0]!;

// The package IDs that the "repositories" links of the cards search for.
const getLinkedPackageIds = () =>
  screen.getAllByRole('link', { name: /repositories/ }).map((link) => {
    const search = defaultParseSearch(
      new URL(link.getAttribute('href')!, 'http://localhost').search
    ) as { pkgId?: string };

    return search.pkgId;
  });

// The query for a page of vulnerabilities, as opposed to the query for the total count.
const lastPageQuery = () =>
  mocks.vulnerabilities.mock.calls
    .map((call) => call[0].query as VulnerabilitiesQuery)
    .filter((query) => query.limit !== 1)
    .at(-1);

beforeEach(() => {
  vi.clearAllMocks();
  nameTag = 'vuln';
  useUserSettingsStore.setState({
    packageIdType: packageIdTypeSchema.enum.PURL,
  });
  mocks.advisors.mockResolvedValue({ data: ['OSV'] });
  mocks.vulnerabilities.mockImplementation(
    async ({ query }: { query?: VulnerabilitiesQuery }) =>
      getVulnerabilitiesPage(query)
  );
});

describe('organization vulnerabilities table', () => {
  it('is compiled by React Compiler', () => {
    expect(OrganizationVulnerabilitiesComponent.toString()).toContain(
      'react.memo_cache_sentinel'
    );
  });

  it('shows the vulnerabilities once they have loaded', async () => {
    let resolvePage = (response: VulnerabilitiesResponse) => {
      void response;
    };
    mocks.vulnerabilities.mockImplementation(
      ({ query }: { query?: VulnerabilitiesQuery }) =>
        query?.limit === 1
          ? Promise.resolve(getVulnerabilitiesPage(query))
          : new Promise<VulnerabilitiesResponse>((resolve) => {
              resolvePage = resolve;
            })
    );

    const { container } = renderVulnerabilities();

    await waitFor(() => expect(lastPageQuery()).toBeDefined());
    expect(screen.queryByText(/^Summary of/)).not.toBeInTheDocument();

    await act(async () => resolvePage(getVulnerabilitiesPage(lastPageQuery())));

    expect(await findVulnerability('vuln-1')).toBeVisible();
    expect(screen.getByText('Summary of vuln-3')).toBeVisible();
    expect(container).toHaveTextContent('Vulnerabilities (25 in total)');
  });

  it('applies filters from the URL', async () => {
    const { container, router } = renderVulnerabilities();

    expect(await findVulnerability('vuln-1')).toBeVisible();

    await act(() =>
      router.navigate({
        to: '.',
        search: {
          rating: ['HIGH'],
          externalId: 'CVE-2026',
          advisor: ['OSV', 'VulnerableCode'],
          pkgId: 'library',
        },
      })
    );

    expect(await findVulnerability('filtered-1')).toBeVisible();
    expect(screen.queryByText('Summary of vuln-1')).not.toBeInTheDocument();
    expect(lastPageQuery()).toMatchObject({
      rating: 'HIGH',
      externalId: 'CVE-2026',
      advisors: 'OSV,VulnerableCode',
      purl: 'library',
    });
    expect(lastPageQuery()).not.toHaveProperty('identifier');
    expect(container).toHaveTextContent(
      'Vulnerabilities (25 in total, 13 matching filters)'
    );
  });

  it('applies sorting and the page from the URL', async () => {
    const { router } = renderVulnerabilities();

    expect(await findVulnerability('vuln-1')).toBeVisible();

    await act(() =>
      router.navigate({
        to: '.',
        search: { sortBy: [{ id: 'rating', desc: true }], page: 2 },
      })
    );

    expect(await findVulnerability('sorted-11')).toBeVisible();
    expect(lastPageQuery()).toMatchObject({
      sort: '-rating',
      limit: 10,
      offset: 10,
    });
  });

  it('keeps the filters from the URL when going to the next page', async () => {
    const { router, user } = renderVulnerabilities();

    expect(await findVulnerability('vuln-1')).toBeVisible();

    await act(() => router.navigate({ to: '.', search: { rating: ['HIGH'] } }));
    expect(await findVulnerability('filtered-1')).toBeVisible();

    await user.click(screen.getByRole('link', { name: 'Go to next page' }));

    await waitFor(() =>
      expect(router.state.location.search).toEqual({
        rating: ['HIGH'],
        page: 2,
      })
    );
    expect(await findVulnerability('filtered-11')).toBeVisible();
    expect(lastPageQuery()).toMatchObject({ rating: 'HIGH', offset: 10 });
  });

  it('follows a change of the package identifier type', async () => {
    renderVulnerabilities('?pkgId=library');

    expect(await findVulnerability('filtered-1')).toBeVisible();
    expect(lastPageQuery()).toMatchObject({ purl: 'library' });
    expect(getLinkedPackageIds()[0]).toBe(purlOf('filtered-1'));

    act(() =>
      useUserSettingsStore.setState({
        packageIdType: packageIdTypeSchema.enum.ORT_ID,
      })
    );

    await waitFor(() =>
      expect(lastPageQuery()).toMatchObject({ identifier: 'library' })
    );
    expect(lastPageQuery()).not.toHaveProperty('purl');
    await waitFor(() =>
      expect(getLinkedPackageIds()[0]).toBe(
        identifierToString(identifierOf('filtered-1'))
      )
    );
  });

  it('expands and collapses a row', async () => {
    const { user } = renderVulnerabilities();

    expect(await findVulnerability('vuln-1')).toBeVisible();
    expect(screen.queryByText(/^Description of/)).not.toBeInTheDocument();

    await user.click(getDetailsButton('vuln-1'));

    expect(await screen.findByText('Description of vuln-1')).toBeVisible();
    expect(
      getDetailsButton('vuln-1').querySelector('.lucide-chevron-up')
    ).toBeInTheDocument();
    expect(screen.queryByText('Description of vuln-2')).not.toBeInTheDocument();
    expect(
      getDetailsButton('vuln-2').querySelector('.lucide-chevron-down')
    ).toBeInTheDocument();

    await user.click(getDetailsButton('vuln-1'));

    await waitFor(() =>
      expect(
        screen.queryByText('Description of vuln-1')
      ).not.toBeInTheDocument()
    );
    expect(
      getDetailsButton('vuln-1').querySelector('.lucide-chevron-down')
    ).toBeInTheDocument();
  });

  it('expands the row marked in the URL', async () => {
    renderVulnerabilities('?marked=1');

    expect(await screen.findByText('Description of vuln-2')).toBeVisible();
    expect(screen.queryByText('Description of vuln-1')).not.toBeInTheDocument();
  });

  it('replaces the rows and collapses them when the query data is refreshed', async () => {
    const { queryClient, user } = renderVulnerabilities();

    expect(await findVulnerability('vuln-1')).toBeVisible();
    await user.click(getDetailsButton('vuln-1'));
    expect(await screen.findByText('Description of vuln-1')).toBeVisible();

    nameTag = 'renamed';
    await act(() => queryClient!.invalidateQueries());

    expect(await findVulnerability('renamed-1')).toBeVisible();
    expect(screen.queryByText('Summary of vuln-1')).not.toBeInTheDocument();
    // The table collapses expanded rows when it receives new data.
    expect(screen.queryByText(/^Description of/)).not.toBeInTheDocument();
  });

  it('offers the advisor filter once more than one advisor is known', async () => {
    const { queryClient } = renderVulnerabilities();

    expect(await findVulnerability('vuln-1')).toBeVisible();
    expect(screen.getByText('Rating')).toBeVisible();
    expect(screen.queryByText('Advisor')).not.toBeInTheDocument();

    mocks.advisors.mockResolvedValue({ data: ['OSV', 'VulnerableCode'] });
    await act(() => queryClient!.invalidateQueries());

    expect(await screen.findByText('Advisor')).toBeVisible();
  });
});
