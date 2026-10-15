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

import { defaultParseSearch } from '@tanstack/react-router';
import { screen } from '@testing-library/react';
import { expect } from 'vitest';

// A response of the API with one page of the given items.
export const pageOf = <T>(
  data: T[],
  { limit = 10, offset = 0, totalCount = data.length } = {}
) => ({ data: { data, pagination: { limit, offset, totalCount } } });

// The texts of the cells in the column with the given header, from top to bottom.
// Reads the table through the DOM instead of role queries. A role query per
// row and cell is slow when the tests run with coverage, and this runs on every
// retry of waitFor.
export const getColumnTexts = (header: string) => {
  const columnIndex = Array.from(
    screen.getByRole('table').querySelectorAll('thead th')
  ).findIndex((columnHeader) => columnHeader.textContent.includes(header));
  expect(columnIndex).toBeGreaterThanOrEqual(0);

  return Array.from(
    screen.getByRole('table').querySelectorAll('tbody tr'),
    (row) => row.querySelectorAll('td')[columnIndex]?.textContent
  );
};

// The path and the parsed search parameters of the page a link leads to.
export const getLinkTarget = (link: HTMLElement) => {
  const target = new URL(link.getAttribute('href')!, 'http://localhost');

  return {
    pathname: target.pathname,
    search: defaultParseSearch(target.search),
  };
};

// The organization, product and repository level of the page with the given subpath, such as
// 'search-package', with the routes to render it and the query fields that identify the level.
// The repository pages live under the pathless '_repo-layout' route, which is part of their
// route IDs.
// With `layout`, the page is rendered by the layout route of the subpage (its `route.tsx`)
// instead of its index route, and has that route's ID.
export const hierarchyLevels = (subpage: string, { layout = false } = {}) => {
  const page = layout ? subpage : `${subpage}/`;

  return [
    {
      level: 'organization',
      path: `/organizations/1/${subpage}`,
      routes: [{ path: `/organizations/$orgId/${page}` }],
      hierarchyId: { organizationId: 1 },
    },
    {
      level: 'product',
      path: `/organizations/1/products/2/${subpage}`,
      routes: [{ path: `/organizations/$orgId/products/$productId/${page}` }],
      hierarchyId: { productId: 2 },
    },
    {
      level: 'repository',
      path: `/organizations/1/products/2/repositories/3/${subpage}`,
      routes: [
        {
          path: '/organizations/$orgId/products/$productId/repositories/$repoId',
          children: [{ id: '_repo-layout', children: [{ path: page }] }],
        },
      ],
      hierarchyId: { repositoryId: 3 },
    },
  ] as const;
};
