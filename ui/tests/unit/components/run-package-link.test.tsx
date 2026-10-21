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
import { describe, expect, it, vi } from 'vitest';

import { TooltipProvider } from '@/components/ui/tooltip';
import { identifierToString } from '@/helpers/identifier-conversion';
import { RunPackageLink } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/-components/run-package-link';
import { packageIdTypeSchema, type PackageIdType } from '@/schemas';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';
import { getLinkTarget } from '../fixtures/table';

const identifier = {
  type: 'Maven',
  namespace: 'com.example',
  name: 'library',
  version: '1.0',
};
const ortId = identifierToString(identifier);
const purl = 'pkg:maven/com.example/library@1.0';
const runPath = '/organizations/1/products/2/repositories/3/runs/4';
// Every package link finds the package by an exact match of its ORT ID and
// opens it, whichever identifier it shows.
const packageSearch = {
  pkgId: ortId,
  pkgIdType: 'ORT_ID',
  pkgIdMatchType: 'exact',
  marked: ortId,
};

const renderLink = ({
  linkPurl,
  packageIdType,
  linkProjectWhenPurlIsNull,
}: {
  linkPurl: string | null | undefined;
  packageIdType: PackageIdType;
  linkProjectWhenPurlIsNull?: boolean;
}) =>
  renderInteractiveWithRouter(
    <TooltipProvider>
      <RunPackageLink
        identifier={identifier}
        purl={linkPurl}
        packageIdType={packageIdType}
        params={{ orgId: '1', productId: '2', repoId: '3', runIndex: '4' }}
        linkProjectWhenPurlIsNull={linkProjectWhenPurlIsNull}
      />
    </TooltipProvider>,
    { path: '/finding', routes: [{ path: '/finding' }] }
  );

describe('RunPackageLink', () => {
  it.each([
    {
      case: 'the PURL when PURLs are shown',
      linkPurl: purl,
      packageIdType: packageIdTypeSchema.enum.PURL,
      linkProjectWhenPurlIsNull: false,
      linkText: purl,
      pathname: `${runPath}/packages`,
      search: packageSearch,
    },
    {
      case: 'the ORT ID when the PURL is empty',
      linkPurl: '',
      packageIdType: packageIdTypeSchema.enum.PURL,
      linkProjectWhenPurlIsNull: false,
      linkText: ortId,
      pathname: `${runPath}/packages`,
      search: packageSearch,
    },
    {
      case: 'the ORT ID when ORT IDs are shown',
      linkPurl: purl,
      packageIdType: packageIdTypeSchema.enum.ORT_ID,
      linkProjectWhenPurlIsNull: false,
      linkText: ortId,
      pathname: `${runPath}/packages`,
      search: packageSearch,
    },
    {
      case: 'the project when a missing PURL means a project',
      linkPurl: null,
      packageIdType: packageIdTypeSchema.enum.PURL,
      linkProjectWhenPurlIsNull: true,
      linkText: ortId,
      pathname: `${runPath}/projects`,
      search: { projectId: ortId, projectIdMatchType: 'exact', marked: ortId },
    },
    {
      case: 'the project when the API leaves out the PURL',
      linkPurl: undefined,
      packageIdType: packageIdTypeSchema.enum.PURL,
      linkProjectWhenPurlIsNull: true,
      linkText: ortId,
      pathname: `${runPath}/projects`,
      search: { projectId: ortId, projectIdMatchType: 'exact', marked: ortId },
    },
    {
      case: 'the package when a missing PURL does not mean a project',
      linkPurl: null,
      packageIdType: packageIdTypeSchema.enum.PURL,
      linkProjectWhenPurlIsNull: false,
      linkText: ortId,
      pathname: `${runPath}/packages`,
      search: packageSearch,
    },
  ])('links $case', async ({ linkText, pathname, search, ...props }) => {
    renderLink(props);

    expect(
      getLinkTarget(await screen.findByRole('link', { name: linkText }))
    ).toEqual({ pathname, search });
  });

  it('copies the shown identifier', async () => {
    const { user } = renderLink({
      linkPurl: purl,
      packageIdType: packageIdTypeSchema.enum.PURL,
    });
    const writeText = vi.spyOn(navigator.clipboard, 'writeText');

    await user.click(
      await screen.findByRole('button', { name: 'Copy to clipboard' })
    );

    expect(writeText).toHaveBeenCalledExactlyOnceWith(purl);
  });
});
