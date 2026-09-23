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
import { screen } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';

import type { VulnerabilityWithDetails } from '@/api';
import { TooltipProvider } from '@/components/ui/tooltip';
import { identifierToString } from '@/helpers/identifier-conversion';
import { Route } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/vulnerabilities/index';
import { packageIdTypeSchema } from '@/schemas';
import { useUserSettingsStore } from '@/store/user-settings.store';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';

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
  mocks.vulnerabilities.mockResolvedValue({
    data: {
      data: [vulnerability],
      pagination: { totalCount: 1, limit: 10, offset: 0 },
    },
  });
});

it('links a vulnerability for a package with an empty PURL by its ORT ID', async () => {
  const id = identifierToString(vulnerability.identifier);
  renderInteractiveWithRouter(
    <TooltipProvider>
      <VulnerabilitiesComponent />
    </TooltipProvider>,
    {
      path: '/organizations/1/products/2/repositories/3/runs/4/vulnerabilities',
      routes: [
        {
          path: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/vulnerabilities/',
        },
      ],
      withQueryClient: true,
    }
  );

  const link = await screen.findByRole('link', { name: id });
  const target = new URL(link.getAttribute('href')!, 'http://localhost');

  expect(target.pathname).toBe(
    '/organizations/1/products/2/repositories/3/runs/4/packages'
  );
  expect(defaultParseSearch(target.search)).toEqual({
    pkgId: id,
    pkgIdType: packageIdTypeSchema.enum.ORT_ID,
    marked: '0',
  });
});
