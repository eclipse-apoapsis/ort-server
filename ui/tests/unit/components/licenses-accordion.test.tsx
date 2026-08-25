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

import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { Identifier } from '@/api';
import { LicensesAccordion } from '@/components/licenses/licenses-accordion';
import { renderStaticWithRouter } from '../fixtures/router-harness';

const mocks = vi.hoisted(() => ({
  queryState: {
    data: undefined as string[] | undefined,
    isPending: false,
  },
  getOptions: vi.fn(),
}));

vi.mock('@tanstack/react-query', () => ({
  useQuery: () => mocks.queryState,
}));

vi.mock('@/api/@tanstack/react-query.gen', () => ({
  getRunDetectedLicensesForIdentifierOptions: mocks.getOptions,
}));

vi.mock('@/components/ui/accordion', () => ({
  Accordion: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  AccordionItem: ({ children }: { children: ReactNode }) => (
    <div>{children}</div>
  ),
  AccordionTrigger: ({ children }: { children: ReactNode }) => (
    <div>{children}</div>
  ),
  AccordionContent: ({ children }: { children: ReactNode }) => (
    <div>{children}</div>
  ),
}));

vi.mock('@/components/licenses', () => ({
  SpdxExpressionBadgeGroup: ({
    expression,
    suffix,
  }: {
    expression: string;
    suffix?: ReactNode;
  }) => (
    <span>
      {expression}
      {suffix}
    </span>
  ),
}));

vi.mock('@/components/loading-indicator', () => ({
  LoadingIndicator: () => <span>Loading data...</span>,
}));

const identifier: Identifier = {
  type: 'Maven',
  namespace: 'com.example',
  name: 'some library',
  version: '1.0/rc%1',
};

const runPath = '/organizations/1/products/2/repositories/3/runs/4' as const;

const renderAccordion = (
  accordionIdentifier = identifier,
  runId = 42,
  declaredLicenses: string[] = []
) =>
  renderStaticWithRouter(
    <LicensesAccordion
      runId={runId}
      identifier={accordionIdentifier}
      declaredLicenses={declaredLicenses}
    />,
    {
      path: runPath,
      routes: [
        {
          path: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex',
        },
        {
          path: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/license-findings',
        },
      ],
    }
  );

const getLinkUrls = (markup: string) =>
  Array.from(
    markup.matchAll(/href="([^"]+)"/g),
    ([, href]) => new URL(href!.replaceAll('&amp;', '&'), 'http://localhost')
  );

describe('LicensesAccordion', () => {
  beforeEach(() => {
    mocks.queryState.data = undefined;
    mocks.queryState.isPending = false;
    mocks.getOptions.mockReset();
    mocks.getOptions.mockReturnValue({});
  });

  it('shows a loading indicator while licenses are loading', async () => {
    mocks.queryState.isPending = true;

    expect(await renderAccordion()).toContain('Loading data...');
  });

  it('shows messages when no licenses were declared or detected', async () => {
    mocks.queryState.data = [];

    const markup = await renderAccordion();

    expect(markup).toContain('No declared licenses.');
    expect(markup).toContain('No licenses detected.');
  });

  it('renders comma-separated declared and detected licenses in labeled rows', async () => {
    mocks.queryState.data = ['Apache-2.0', 'GPL-2.0-only'];

    const markup = await renderAccordion(identifier, 42, [
      'MIT',
      'BSD-3-Clause',
    ]);

    expect(markup).toContain('Declared:');
    expect(markup).toMatch(/MIT.*?,.*?BSD-3-Clause/);
    expect(markup).toContain('Detected:');
    expect(markup).toMatch(/Apache-2\.0.*?,.*?GPL-2\.0-only/);
  });

  it('passes the raw identifier to the generated client for URL encoding', async () => {
    mocks.queryState.data = ['MIT'];

    await renderAccordion();

    expect(mocks.getOptions).toHaveBeenCalledWith({
      path: {
        runId: 42,
        identifier: 'Maven:com.example:some library:1.0/rc%1',
      },
    });
  });

  it('renders one link for a detected license', async () => {
    mocks.queryState.data = ['Apache-2.0'];

    const markup = await renderAccordion();
    const links = getLinkUrls(markup);

    expect(markup).toContain('Apache-2.0');
    expect(links).toHaveLength(1);
    expect(links[0]?.pathname).toBe(`${runPath}/license-findings`);
  });

  it('uses a project ORT identifier in the query and deep link', async () => {
    const projectIdentifier: Identifier = {
      type: 'Gradle',
      namespace: '',
      name: 'example-project',
      version: '',
    };
    mocks.queryState.data = ['MIT'];

    const markup = await renderAccordion(projectIdentifier, 84);

    expect(mocks.getOptions).toHaveBeenCalledWith({
      path: {
        runId: 84,
        identifier: 'Gradle::example-project:',
      },
    });
    expect(getLinkUrls(markup)[0]?.searchParams.get('packageMarked')).toBe(
      'Gradle::example-project:'
    );
  });

  it('keeps multiple expressions in independent exact links', async () => {
    mocks.queryState.data = ['MIT', 'MIT AND Apache-2.0'];

    const links = getLinkUrls(await renderAccordion());

    expect(links).toHaveLength(2);
    expect(Object.fromEntries(links[0]!.searchParams)).toEqual({
      detectedLicense: '["MIT"]',
      marked: 'MIT',
      packageMarked: 'Maven:com.example:some library:1.0/rc%1',
      page: '1',
      packagePage: '1',
      findingsPage: '1',
    });
    expect(Object.fromEntries(links[1]!.searchParams)).toEqual({
      detectedLicense: '["MIT AND Apache-2.0"]',
      marked: 'MIT AND Apache-2.0',
      packageMarked: 'Maven:com.example:some library:1.0/rc%1',
      page: '1',
      packagePage: '1',
      findingsPage: '1',
    });
  });
});
