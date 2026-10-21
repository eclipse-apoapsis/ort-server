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

import { act, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { GetRunRuleViolationsData, RuleViolation } from '@/api';
import { identifierToString } from '@/helpers/identifier-conversion';
import { Route } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/rule-violations/index';
import { packageIdTypeSchema } from '@/schemas';
import { useUserSettingsStore } from '@/store/user-settings.store';
import { renderRunPage } from '../fixtures/finding-tables';
import { getLinkTarget, pageOf } from '../fixtures/table';

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
  mocks.violations.mockResolvedValue(pageOf([violation]));

  renderRunPage(RuleViolationsComponent, 'rule-violations');
};

it.each([
  {
    purl,
    linkText: purl,
    table: 'packages',
    search: {
      pkgId: id,
      pkgIdType: 'ORT_ID',
      pkgIdMatchType: 'exact',
      marked: id,
    },
  },
  {
    purl: null,
    linkText: id,
    table: 'projects',
    search: { projectId: id, projectIdMatchType: 'exact', marked: id },
  },
])(
  'links a rule violation with PURL $purl to $table',
  async ({ purl, linkText, table, search }) => {
    renderViolation(purl);

    expect(
      getLinkTarget(await screen.findByRole('link', { name: linkText }))
    ).toEqual({
      pathname: `/organizations/1/products/2/repositories/3/runs/4/${table}`,
      search,
    });
  }
);

describe('rule violations table', () => {
  type ViolationsQuery = NonNullable<GetRunRuleViolationsData['query']>;

  const purlOf = (name: string) => `pkg:maven/com.example/${name}@1.0`;

  const getViolationsPage = (query: ViolationsQuery = {}) => {
    const name = query.rule ? 'filtered' : 'violation';
    const violation: RuleViolation = {
      id: { ...identifier, name },
      purl: purlOf(name),
      howToFix: '',
      message: 'A violation',
      rule: query.rule ?? 'TestRule',
      severity: 'WARNING',
    };

    return pageOf([violation], {
      limit: query.limit,
      offset: query.offset,
      totalCount: query.rule ? 1 : 5,
    });
  };

  beforeEach(() => {
    mocks.violations.mockImplementation(
      async ({ query }: { query?: ViolationsQuery }) => getViolationsPage(query)
    );
  });

  it('applies filters from the URL', async () => {
    const { container, router } = renderRunPage(
      RuleViolationsComponent,
      'rule-violations'
    );

    expect(
      await screen.findByRole('link', { name: purlOf('violation') })
    ).toBeInTheDocument();

    await act(() =>
      router.navigate({
        to: '.',
        search: { severity: ['ERROR'], rule: ['RuleA', 'RuleB'] },
      })
    );

    await waitFor(() =>
      expect(
        screen.getByRole('link', { name: purlOf('filtered') })
      ).toBeVisible()
    );
    expect(
      screen.queryByRole('link', { name: purlOf('violation') })
    ).not.toBeInTheDocument();
    expect(mocks.violations.mock.calls.at(-1)?.[0].query).toMatchObject({
      severity: 'ERROR',
      rule: 'RuleA,RuleB',
    });
    expect(container).toHaveTextContent(
      'Rule Violations (5 in total, 1 matching filters)'
    );
  });
});
