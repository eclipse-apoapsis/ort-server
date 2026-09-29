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
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';

const mocks = vi.hoisted(() => ({
  packages: [] as Package[],
  packageQueries: [] as GetRunPackagesData['query'][],
}));

vi.mock('@tanstack/react-query', async (importOriginal) => {
  const original =
    await importOriginal<typeof import('@tanstack/react-query')>();

  return {
    ...original,
    useSuspenseQuery: ({
      queryKey: [{ _id, query }],
    }: {
      queryKey: [{ _id: string; query?: GetRunPackagesData['query'] }];
    }) => {
      switch (_id) {
        case 'getRepositoryRun':
          return { data: { id: 1 } };
        case 'getRunPackageLicenses':
          return {
            data: {
              processedDeclaredLicenses: [],
              unmappedDeclaredLicenses: [],
            },
          };
        case 'getRunPackages':
          mocks.packageQueries.push(query);
          return {
            data: {
              data: mocks.packages,
              pagination: {
                limit: query?.limit,
                offset: query?.offset,
                totalCount: 30,
              },
            },
            error: null,
            isError: false,
          };
        default:
          throw new Error(`Unexpected query '${_id}'.`);
      }
    },
  };
});

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
  renderInteractiveWithRouter(<PackagesComponent />, {
    path: `/organizations/1/products/2/repositories/3/runs/4/packages/${search}`,
    routes: [
      {
        path: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/packages/',
      },
    ],
  });

describe('packages view', () => {
  beforeEach(() => {
    mocks.packages = [];
    mocks.packageQueries = [];
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
      mocks.packages = [
        createPackage('dated', '2024-05-06T07:08:09Z'),
        createPackage('null-date', null),
        createPackage('no-date'),
      ];

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
      expect(screen.getAllByText('Published:')).toHaveLength(1);
      expect(screen.queryByText('Unknown')).not.toBeInTheDocument();
    }
  );

  it('sorts by publication date in both directions after the other sort fields, starting from the first page', async () => {
    mocks.packages = [createPackage('example', '2024-05-06T07:08:09Z')];
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
    expect(mocks.packageQueries.at(-1)).toMatchObject({
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
    expect(mocks.packageQueries.at(-1)).toMatchObject({
      offset: 0,
      sort: 'identifier,-publishedAt',
    });
  });
});
