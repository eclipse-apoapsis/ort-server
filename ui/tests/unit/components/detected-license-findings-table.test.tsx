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
import { beforeEach, expect, it, vi } from 'vitest';

import type { LicenseFinding } from '@/api';
import { createLicenseFindingCurationTemplate } from '@/lib/license-finding-curation';
import { buildSwhBrowseUrl } from '@/lib/software-heritage';
import { DetectedLicenseFindingsTable } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/license-findings/-components/detected-license-findings-table';
import { renderRunPage } from '../fixtures/finding-tables';
import { pageOf } from '../fixtures/table';

const mocks = vi.hoisted(() => ({
  findings: vi.fn(),
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  getRunDetectedLicenseFindings: mocks.findings,
}));

const finding: LicenseFinding = {
  path: 'package/package.json',
  startLine: 18,
  endLine: 18,
  score: 100,
  scanner: 'DOS 1.0.0 (ScanCode 32.5.0)',
};

const renderTable = (identifier: string, license: string, purl?: string) =>
  renderRunPage(
    () => (
      <DetectedLicenseFindingsTable
        runId={1}
        identifier={identifier}
        license={license}
        purl={purl}
      />
    ),
    'license-findings'
  );

beforeEach(() => {
  vi.clearAllMocks();
  mocks.findings.mockResolvedValue(pageOf([finding]));
});

it('copies a curation template before the Software Heritage link', async () => {
  const identifier = 'NPM::abs-svg-path:0.1.1';
  const license = 'MIT';
  const purl = 'pkg:npm/abs-svg-path@0.1.1';
  const { user } = renderTable(identifier, license, purl);
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

it('names its panel after the package and its license', async () => {
  const identifier = 'Maven:com.example:some library:1.0/rc%1';
  const license = 'MIT AND Apache-2.0';
  renderTable(identifier, license);

  // Two panels for the same package under different licenses stay distinguishable.
  expect(
    await screen.findByRole('region', {
      name: `License findings for ${identifier} under ${license}`,
    })
  ).toBeVisible();
});
