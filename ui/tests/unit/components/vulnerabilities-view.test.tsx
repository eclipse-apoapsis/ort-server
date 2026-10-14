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

import { act, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type {
  GetRunVulnerabilitiesData,
  VulnerabilityWithDetails,
} from '@/api';
import { identifierToString } from '@/helpers/identifier-conversion';
import { Route } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/vulnerabilities/index';
import { packageIdTypeSchema } from '@/schemas';
import { useUserSettingsStore } from '@/store/user-settings.store';
import { renderRunPage } from '../fixtures/finding-tables';
import { createDeferred } from '../fixtures/loader-test-utils';
import { getLinkTarget, pageOf } from '../fixtures/table';

const mocks = vi.hoisted(() => ({
  run: vi.fn(),
  advisors: vi.fn(),
  vulnerabilities: vi.fn(),
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  getRepositoryRun: mocks.run,
  getRunVulnerabilityAdvisors: mocks.advisors,
  getRunVulnerabilities: mocks.vulnerabilities,
}));

vi.mock('@/components/resolutions', () => ({
  Resolutions: () => null,
}));

const VulnerabilitiesComponent = Route.options.component!;

const vulnerability: VulnerabilityWithDetails = {
  advisor: { name: 'Advisor' },
  identifier: {
    type: 'Maven',
    namespace: 'com.example',
    name: 'library',
    version: '1.0',
  },
  purl: '',
  rating: 'HIGH',
  resolutions: [],
  unappliedResolutions: [],
  vulnerability: {
    externalId: 'CVE-2026-1234',
    references: [],
  },
};

beforeEach(() => {
  vi.clearAllMocks();
  useUserSettingsStore.setState({
    packageIdType: packageIdTypeSchema.enum.PURL,
  });
  mocks.run.mockResolvedValue({ data: { id: 42, jobs: { advisor: {} } } });
  mocks.advisors.mockResolvedValue({ data: [] });
  mocks.vulnerabilities.mockResolvedValue(pageOf([vulnerability]));
});

it('links a vulnerability for a package with an empty PURL by its ORT ID', async () => {
  const id = identifierToString(vulnerability.identifier);
  renderRunPage(VulnerabilitiesComponent, 'vulnerabilities');

  expect(getLinkTarget(await screen.findByRole('link', { name: id }))).toEqual({
    pathname: '/organizations/1/products/2/repositories/3/runs/4/packages',
    search: {
      pkgId: id,
      pkgIdType: packageIdTypeSchema.enum.ORT_ID,
      marked: '0',
    },
  });
});

describe('vulnerabilities table', () => {
  type VulnerabilitiesQuery = NonNullable<GetRunVulnerabilitiesData['query']>;

  const idOf = (name: string) =>
    identifierToString({ ...vulnerability.identifier, name });

  const getVulnerabilitiesPage = (query: VulnerabilitiesQuery = {}) =>
    pageOf(
      [
        {
          ...vulnerability,
          identifier: {
            ...vulnerability.identifier,
            name: query.externalId ? 'filtered' : 'library',
          },
        },
      ],
      {
        limit: query.limit,
        offset: query.offset,
        totalCount: query.externalId ? 1 : 5,
      }
    );

  const renderVulnerabilities = () =>
    renderRunPage(VulnerabilitiesComponent, 'vulnerabilities');

  it('is compiled by React Compiler', () => {
    expect(VulnerabilitiesComponent.toString()).toContain(
      'react.memo_cache_sentinel'
    );
  });

  it('applies filters from the URL once the filtered data has loaded', async () => {
    const filtered =
      createDeferred<ReturnType<typeof getVulnerabilitiesPage>>();
    mocks.vulnerabilities.mockImplementation(
      ({ query }: { query?: VulnerabilitiesQuery }) =>
        query?.externalId
          ? filtered.promise
          : Promise.resolve(getVulnerabilitiesPage(query))
    );

    const { container, router, user } = renderVulnerabilities();

    const link = await screen.findByRole('link', { name: idOf('library') });
    await user.click(within(link.closest('tr')!).getAllByRole('button')[0]!);
    expect(
      await screen.findByRole('button', { name: 'Resolve vulnerability' })
    ).toBeVisible();

    await act(() =>
      router.navigate({
        to: '.',
        search: { rating: ['HIGH'], externalId: 'CVE-2026-9999' },
      })
    );

    await waitFor(() =>
      expect(mocks.vulnerabilities.mock.calls.at(-1)?.[0].query).toMatchObject({
        rating: 'HIGH',
        externalId: 'CVE-2026-9999',
      })
    );
    expect(
      screen.queryByRole('link', { name: idOf('filtered') })
    ).not.toBeInTheDocument();

    await act(async () =>
      filtered.resolve(getVulnerabilitiesPage({ externalId: 'CVE-2026-9999' }))
    );

    await waitFor(() =>
      expect(screen.getByRole('link', { name: idOf('filtered') })).toBeVisible()
    );
    expect(
      screen.queryByRole('link', { name: idOf('library') })
    ).not.toBeInTheDocument();
    expect(container).toHaveTextContent(
      'Vulnerabilities (5 in total, 1 matching filters)'
    );
    // The table collapses expanded rows when it receives new data.
    expect(
      screen.queryByRole('button', { name: 'Resolve vulnerability' })
    ).not.toBeInTheDocument();
  });
});
