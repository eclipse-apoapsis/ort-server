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

import { screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { RunWithPackage } from '@/api/types.gen';
import { Route } from '@/routes/organizations/$orgId/search-package';
import { useUserSettingsStore } from '@/store/user-settings.store';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';

const mocks = vi.hoisted(() => ({
  runs: [] as RunWithPackage[],
}));

vi.mock('@tanstack/react-query', async (importOriginal) => {
  const original =
    await importOriginal<typeof import('@tanstack/react-query')>();

  return {
    ...original,
    useQuery: () => ({
      data: mocks.runs,
      error: null,
      isError: false,
      isPending: false,
    }),
    useSuspenseQuery: () => ({ data: { name: 'Product', url: 'Repository' } }),
  };
});

const createRun = (
  ortRunIndex: number,
  name: string,
  purlName: string
): RunWithPackage => ({
  createdAt: '2026-01-01T00:00:00Z',
  organizationId: 1,
  ortRunId: ortRunIndex,
  ortRunIndex,
  packageId: {
    type: 'Maven',
    namespace: 'org.example',
    name,
    version: '1.0',
  },
  productId: 2,
  purl: `pkg:maven/org.example/${purlName}@1.0`,
  repositoryId: 3,
  revision: `revision-${ortRunIndex}`,
});

const runs = [
  createRun(1, 'zulu', 'alpha'),
  createRun(2, 'alpha', 'zulu'),
  createRun(3, 'mike', 'mike'),
];

const expectedIdentifiers = {
  ORT_ID: [
    'Maven:org.example:alpha:1.0',
    'Maven:org.example:mike:1.0',
    'Maven:org.example:zulu:1.0',
  ],
  PURL: [
    'pkg:maven/org.example/alpha@1.0',
    'pkg:maven/org.example/mike@1.0',
    'pkg:maven/org.example/zulu@1.0',
  ],
} as const;

const SearchPackageComponent = Route.options.component!;

const getDisplayedPackageIdentifiers = () => {
  const headers = screen.getAllByRole('columnheader');
  const packageColumnIndex = headers.findIndex((header) =>
    header.textContent.includes('Matching Package')
  );
  expect(packageColumnIndex).toBeGreaterThanOrEqual(0);

  return screen
    .getAllByRole('row')
    .slice(1)
    .map(
      (row) =>
        within(row).getAllByRole('cell').at(packageColumnIndex)?.textContent
    );
};

describe('search package sorting', () => {
  beforeEach(() => {
    mocks.runs = runs;
  });

  afterEach(() => {
    useUserSettingsStore.setState({ packageIdType: 'ORT_ID' });
  });

  it.each(['ORT_ID', 'PURL'] as const)(
    'sorts the displayed %s identifiers',
    async (packageIdType) => {
      useUserSettingsStore.setState({ packageIdType });

      const { user } = renderInteractiveWithRouter(<SearchPackageComponent />, {
        path: '/organizations/1/search-package?pkgId=example',
        routes: [{ path: '/organizations/$orgId/search-package/' }],
      });

      const matchingPackageHeader = (
        await screen.findByText('Matching Package')
      ).closest('th');
      expect(matchingPackageHeader).not.toBeNull();

      await user.click(within(matchingPackageHeader!).getByRole('link'));

      expect(getDisplayedPackageIdentifiers()).toEqual(
        expectedIdentifiers[packageIdType]
      );
    }
  );
});
