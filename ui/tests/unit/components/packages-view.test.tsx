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

import { screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { GetRunPackagesData, Package } from '@/api';
import { formatTimestamp } from '@/lib/utils';
import { Route } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/packages';
import { packageIdTypeSchema } from '@/schemas';
import { useUserSettingsStore } from '@/store/user-settings.store';
import { renderRunPage } from '../fixtures/finding-tables';
import { pageOf } from '../fixtures/table';

type PackagesQuery = GetRunPackagesData['query'];

const mocks = vi.hoisted(() => ({
  run: vi.fn(),
  licenses: vi.fn(),
  packages: vi.fn(),
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  getRepositoryRun: mocks.run,
  getRunPackageLicenses: mocks.licenses,
  getRunPackages: mocks.packages,
}));

vi.mock('@/components/licenses', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/components/licenses')>()),
  LicensesAccordion: () => null,
}));

const createPackage = (name: string, publishedAt?: string | null): Package => ({
  authors: [],
  binaryArtifact: { url: '', hashValue: '', hashAlgorithm: '' },
  curations: [],
  declaredLicenses: [],
  description: '',
  homepageUrl: `https://example.com/${name}`,
  identifier: {
    type: 'Maven',
    namespace: 'org.example',
    name,
    version: '1.0',
  },
  processedDeclaredLicense: { mappedLicenses: {}, unmappedLicenses: [] },
  purl: `pkg:maven/org.example/${name}@1.0`,
  shortestDependencyPaths: [],
  sourceArtifact: { url: '', hashValue: '', hashAlgorithm: '' },
  vcs: { type: '', url: '', revision: '', path: '' },
  vcsProcessed: { type: '', url: '', revision: '', path: '' },
  ...(publishedAt === undefined ? {} : { publishedAt }),
});

const PackagesComponent = Route.options.component!;

const renderPackages = (search = '') =>
  renderRunPage(PackagesComponent, 'packages', search);

// Serves the given packages for every query of packages.
const servePackages = (...packages: Package[]) =>
  mocks.packages.mockImplementation(
    async ({ query }: { query?: PackagesQuery }) =>
      pageOf(packages, {
        limit: query?.limit,
        offset: query?.offset,
        totalCount: 30,
      })
  );

const lastPackagesQuery = () =>
  mocks.packages.mock.calls.at(-1)?.[0].query as PackagesQuery;

const target = createPackage('target');
const targetId = 'Maven:org.example:target:1.0';
// The search of a link to the target package.
const linkSearch = `?${new URLSearchParams({
  pkgId: targetId,
  pkgIdType: 'ORT_ID',
  pkgIdMatchType: 'exact',
  marked: targetId,
})}`;

describe('packages view', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.run.mockResolvedValue({ data: { id: 1 } });
    mocks.licenses.mockResolvedValue({
      data: { processedDeclaredLicenses: [], unmappedDeclaredLicenses: [] },
    });
    servePackages();
  });

  afterEach(() => {
    useUserSettingsStore.setState({
      packageIdType: packageIdTypeSchema.enum.ORT_ID,
    });
  });

  it.each(packageIdTypeSchema.options)(
    'shows the publication date above the homepage in %s mode',
    async (packageIdType) => {
      useUserSettingsStore.setState({ packageIdType });
      servePackages(
        createPackage('dated', '2024-05-06T07:08:09Z'),
        createPackage('null-date', null),
        createPackage('no-date')
      );

      renderPackages();

      const date = await screen.findByText(
        formatTimestamp('2024-05-06T07:08:09Z')
      );
      const homepage = screen.getByRole('link', {
        name: 'https://example.com/dated',
      });

      expect(homepage).toHaveAttribute('href', 'https://example.com/dated');
      expect(
        date.compareDocumentPosition(homepage) &
          Node.DOCUMENT_POSITION_FOLLOWING
      ).toBeTruthy();
      expect(
        screen.getAllByText(formatTimestamp('2024-05-06T07:08:09Z'))
      ).toHaveLength(1);
      expect(screen.queryByText('Unknown')).not.toBeInTheDocument();
    }
  );

  it('labels the publication date in its tooltip', async () => {
    servePackages(createPackage('dated', '2024-05-06T07:08:09Z'));

    const { user } = renderPackages();

    await user.hover(
      await screen.findByText(formatTimestamp('2024-05-06T07:08:09Z'))
    );

    expect(
      (await screen.findAllByText('Publishing timestamp')).length
    ).toBeGreaterThan(0);
  });

  it('sorts by publication date in both directions after the other sort fields, starting from the first page', async () => {
    servePackages(createPackage('example', '2024-05-06T07:08:09Z'));
    const sortBy = encodeURIComponent(
      JSON.stringify([{ id: 'identifier', desc: false }])
    );

    const { router, user } = renderPackages(`?page=3&sortBy=${sortBy}`);

    const chooseSortByPublicationDate = async () => {
      await user.click(await screen.findByRole('button', { name: 'Sort' }));
      await user.click(screen.getByRole('menuitem', { name: 'Published' }));
    };

    await chooseSortByPublicationDate();

    await waitFor(() =>
      expect(router.state.location.search).toMatchObject({
        page: 1,
        sortBy: [
          { id: 'identifier', desc: false },
          { id: 'publishedAt', desc: false },
        ],
      })
    );
    expect(lastPackagesQuery()).toMatchObject({
      offset: 0,
      sort: 'identifier,publishedAt',
    });

    await chooseSortByPublicationDate();

    await waitFor(() =>
      expect(router.state.location.search).toMatchObject({
        sortBy: [
          { id: 'identifier', desc: false },
          { id: 'publishedAt', desc: true },
        ],
      })
    );
    expect(lastPackagesQuery()).toMatchObject({
      offset: 0,
      sort: 'identifier,-publishedAt',
    });
  });

  it('finds a linked package by an exact match and opens it, even if it is not the first row', async () => {
    servePackages(createPackage('other'), target);

    renderPackages(linkSearch);

    expect(await screen.findByText(target.purl!)).toBeVisible();
    expect(
      screen.queryByText('pkg:maven/org.example/other@1.0')
    ).not.toBeInTheDocument();
    expect(lastPackagesQuery()).toMatchObject({
      identifier: targetId,
      identifierMatchType: 'exact',
    });
  });

  it('shows the PURL of a linked package in the filter if PURLs are shown', async () => {
    useUserSettingsStore.setState({
      packageIdType: packageIdTypeSchema.enum.PURL,
    });
    servePackages(target);

    const { user } = renderPackages(linkSearch);

    await user.click(await screen.findByRole('button', { name: 'Package ID' }));

    expect(screen.getByRole('textbox')).toHaveValue(target.purl);
  });

  it('keeps the exact match of a link when the unchanged filter loses focus', async () => {
    useUserSettingsStore.setState({
      packageIdType: packageIdTypeSchema.enum.PURL,
    });
    servePackages(target);

    const { router, user } = renderPackages(linkSearch);

    await user.click(await screen.findByRole('button', { name: 'Package ID' }));
    await user.click(screen.getByRole('textbox'));
    await user.tab();

    expect(router.state.location.search).toMatchObject({
      pkgId: targetId,
      pkgIdType: 'ORT_ID',
      pkgIdMatchType: 'exact',
      marked: targetId,
    });
    expect(lastPackagesQuery()).toMatchObject({
      identifier: targetId,
      identifierMatchType: 'exact',
    });
  });

  it('keeps the exact match of a link when paging', async () => {
    servePackages(target);

    const { user } = renderPackages(linkSearch);

    await user.click(
      await screen.findByRole('link', { name: 'Go to next page' })
    );

    await waitFor(() =>
      expect(lastPackagesQuery()).toMatchObject({
        offset: 10,
        identifier: targetId,
        identifierMatchType: 'exact',
      })
    );
  });

  it('searches by substring again when the filter of a link is edited', async () => {
    servePackages(target);

    const { router, user } = renderPackages(`${linkSearch}&page=2`);

    await user.click(await screen.findByRole('button', { name: 'Package ID' }));
    await user.clear(screen.getByRole('textbox'));
    await user.type(screen.getByRole('textbox'), 'target{Enter}');

    await waitFor(() =>
      expect(router.state.location.search).toEqual({
        page: 1,
        pkgId: 'target',
        pkgIdType: 'ORT_ID',
      })
    );
    expect(lastPackagesQuery()).toMatchObject({ identifier: 'target' });
    expect(lastPackagesQuery()?.identifierMatchType).toBeUndefined();
  });
});
