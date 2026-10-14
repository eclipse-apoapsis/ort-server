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

import type { GetOrganizationVulnerabilitiesData } from '@/api';
import { TooltipProvider } from '@/components/ui/tooltip';
import { identifierToString } from '@/helpers/identifier-conversion';
import { Route } from '@/routes/organizations/$orgId/vulnerabilities/index';
import { packageIdTypeSchema } from '@/schemas';
import { useUserSettingsStore } from '@/store/user-settings.store';
import { createVulnerabilityWithStats } from '../fixtures/finding-tables';
import { createDeferred } from '../fixtures/loader-test-utils';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';
import { pageOf } from '../fixtures/table';

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

const identifierOf = (name: string) => ({
  type: 'Maven',
  namespace: 'com.example',
  name,
  version: '1.0',
});

const purlOf = (name: string) => `pkg:maven/com.example/${name}@1.0`;

// Serve three vulnerabilities per page, named after the query and their position. The total
// query, with a limit of one, always reports 25 vulnerabilities.
const getVulnerabilitiesPage = (query: VulnerabilitiesQuery = {}) => {
  const filtered = Boolean(
    query.rating || query.externalId || query.identifier || query.purl
  );
  const tag = filtered ? 'filtered' : query.sort ? 'sorted' : 'vuln';
  const offset = query.offset ?? 0;
  const data = [1, 2, 3].map((position) =>
    createVulnerabilityWithStats(`${tag}-${offset + position}`)
  );

  return pageOf(data, {
    limit: query.limit,
    offset,
    totalCount: query.limit !== 1 && filtered ? 13 : 25,
  });
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

// The button that expands the row of the given vulnerability.
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
    const page = createDeferred<ReturnType<typeof getVulnerabilitiesPage>>();
    mocks.vulnerabilities.mockImplementation(
      ({ query }: { query?: VulnerabilitiesQuery }) =>
        query?.limit === 1
          ? Promise.resolve(getVulnerabilitiesPage(query))
          : page.promise
    );

    const { container } = renderVulnerabilities();

    await waitFor(() => expect(lastPageQuery()).toBeDefined());
    expect(screen.queryByText(/^Summary of/)).not.toBeInTheDocument();

    await act(async () =>
      page.resolve(getVulnerabilitiesPage(lastPageQuery()))
    );

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

  it('shows the details of an expanded row', async () => {
    const { user } = renderVulnerabilities();

    expect(await findVulnerability('vuln-1')).toBeVisible();

    await user.click(getDetailsButton('vuln-1'));

    expect(await screen.findByText('Description of vuln-1')).toBeVisible();
  });

  it('expands the row marked in the URL', async () => {
    renderVulnerabilities('?marked=1');

    expect(await screen.findByText('Description of vuln-2')).toBeVisible();
    expect(screen.queryByText('Description of vuln-1')).not.toBeInTheDocument();
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
