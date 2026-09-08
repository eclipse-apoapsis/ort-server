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

import { screen, waitFor, within } from '@testing-library/react';
import { expect, it, vi } from 'vitest';

import type { LicenseFinding } from '@/api';
import { TooltipProvider } from '@/components/ui/tooltip';
import { createLicenseFindingCurationTemplate } from '@/lib/license-finding-curation';
import { buildSwhBrowseUrl } from '@/lib/software-heritage';
import { DetectedLicenseFindingsTable } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/license-findings/-components/detected-license-findings-table';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';

const mocks = vi.hoisted(() => ({
  finding: {
    path: 'package/package.json',
    startLine: 18,
    endLine: 18,
    score: 100,
    scanner: 'DOS 1.0.0 (ScanCode 32.5.0)',
  } satisfies LicenseFinding,
}));

const finding = mocks.finding;

vi.mock('@tanstack/react-query', async (importOriginal) => {
  const original =
    await importOriginal<typeof import('@tanstack/react-query')>();

  return {
    ...original,
    useQuery: () => ({
      data: {
        data: [mocks.finding],
        pagination: { limit: 10, offset: 0, totalCount: 1 },
      },
      error: null,
      isError: false,
      isPending: false,
    }),
  };
});

it('copies a curation template before the Software Heritage link', async () => {
  const identifier = 'NPM::abs-svg-path:0.1.1';
  const license = 'MIT';
  const purl = 'pkg:npm/abs-svg-path@0.1.1';
  const { user } = renderInteractiveWithRouter(
    <TooltipProvider>
      <DetectedLicenseFindingsTable
        runId={1}
        identifier={identifier}
        license={license}
        purl={purl}
      />
    </TooltipProvider>,
    {
      path: '/organizations/1/products/2/repositories/3/runs/4/license-findings',
      routes: [
        {
          path: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/license-findings/',
        },
      ],
    }
  );
  const writeText = vi.spyOn(navigator.clipboard, 'writeText');
  const path = await screen.findByText(finding.path);
  const pathCell = path.closest('td');

  expect(pathCell).not.toBeNull();

  const copyButton = within(pathCell!).getByRole('button');
  const swhLink = within(pathCell!).getByRole('link', {
    name: 'Software Heritage',
  });

  expect(Array.from(path.parentElement!.children)).toEqual([
    path,
    copyButton,
    swhLink,
  ]);
  expect(swhLink).toHaveAttribute(
    'href',
    buildSwhBrowseUrl(purl, finding.path, finding.startLine, finding.endLine)
  );

  await user.click(copyButton);

  await waitFor(() => {
    expect(writeText).toHaveBeenCalledWith(
      createLicenseFindingCurationTemplate({
        id: identifier,
        path: finding.path,
        startLine: finding.startLine,
        endLine: finding.endLine,
        detectedLicense: license,
      })
    );
  });
});
