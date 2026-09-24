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

import type { RuleViolation } from '@/api';
import { TooltipProvider } from '@/components/ui/tooltip';
import { identifierToString } from '@/helpers/identifier-conversion';
import { Route } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/rule-violations/index';
import { packageIdTypeSchema } from '@/schemas';
import { useUserSettingsStore } from '@/store/user-settings.store';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';

const mocks = vi.hoisted(() => ({
  run: vi.fn(),
  rules: vi.fn(),
  violations: vi.fn(),
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  getRepositoryRun: mocks.run,
  getRunRuleViolationRules: mocks.rules,
  getRunRuleViolations: mocks.violations,
}));

const RuleViolationsComponent = Route.options.component!;
const identifier = {
  type: 'Maven',
  namespace: 'com.example',
  name: 'library',
  version: '1.0',
};
const id = identifierToString(identifier);
const purl = 'pkg:maven/com.example/library@1.0';

beforeEach(() => {
  vi.clearAllMocks();
  useUserSettingsStore.setState({
    packageIdType: packageIdTypeSchema.enum.PURL,
  });
  mocks.run.mockResolvedValue({ data: { id: 42, jobs: {} } });
  mocks.rules.mockResolvedValue({ data: [] });
});

const renderViolation = (violationPurl: string | null) => {
  const violation: RuleViolation = {
    id: identifier,
    purl: violationPurl,
    howToFix: '',
    message: 'A violation',
    rule: 'TestRule',
    severity: 'WARNING',
  };
  mocks.violations.mockResolvedValue({
    data: {
      data: [violation],
      pagination: { totalCount: 1, limit: 10, offset: 0 },
    },
  });

  renderInteractiveWithRouter(
    <TooltipProvider>
      <RuleViolationsComponent />
    </TooltipProvider>,
    {
      path: '/organizations/1/products/2/repositories/3/runs/4/rule-violations',
      routes: [
        {
          path: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/rule-violations/',
        },
      ],
      withQueryClient: true,
    }
  );
};

it.each([
  {
    purl: '',
    linkText: id,
    table: 'packages',
    search: { pkgId: id, pkgIdType: 'ORT_ID', marked: '0' },
  },
  {
    purl,
    linkText: purl,
    table: 'packages',
    search: { pkgId: purl, pkgIdType: 'PURL', marked: '0' },
  },
  {
    purl: null,
    linkText: id,
    table: 'projects',
    search: { projectId: id, marked: id },
  },
])(
  'links a rule violation with PURL $purl to $table',
  async ({ purl, linkText, table, search }) => {
    renderViolation(purl);

    const link = await screen.findByRole('link', { name: linkText });
    const target = new URL(link.getAttribute('href')!, 'http://localhost');

    expect(target.pathname).toBe(
      `/organizations/1/products/2/repositories/3/runs/4/${table}`
    );
    expect(defaultParseSearch(target.search)).toEqual(search);
  }
);
