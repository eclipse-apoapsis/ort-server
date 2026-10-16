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

import { act, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type {
  SnippetFinding,
  SnippetFindingProvenance,
  SnippetSource,
} from '@/api';
import { SnippetFindingsView } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/snippet-findings/-components/snippet-findings-view';
import { renderRunPage } from '../fixtures/finding-tables';
import { pageOf } from '../fixtures/table';

const mocks = vi.hoisted(() => ({
  getRepositoryRun: vi.fn(),
  getRunSnippetFindingProvenances: vi.fn(),
  getRunProvenanceSnippetFindings: vi.fn(),
  getRunSnippetFindingSnippets: vi.fn(),
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  ...mocks,
}));

const runId = 40;

const createProvenance = (
  id: number,
  snippetFindingCount: number
): SnippetFindingProvenance => ({
  id,
  identifier: {
    type: 'Maven',
    namespace: 'com.example',
    name: `library-${id}`,
    version: '1.0',
  },
  provenanceType: 'ARTIFACT',
  snippetFindingCount,
});

// The provenances show their number of findings, which tells them apart in the tests.
const provenances = [createProvenance(1, 1), createProvenance(2, 2)];

// The findings of provenance 1 have the IDs 11 and 12, those of provenance 2 the ID 21.
const findingsOf = (provenanceId: number): SnippetFinding[] =>
  (provenanceId === 1 ? ['a.c', 'b.c'] : ['c.c']).map((file, index) => ({
    id: provenanceId * 10 + index + 1,
    path: `src/${provenanceId}/${file}`,
    startLine: 1,
    endLine: 10,
    snippetCount: 1,
  }));

const snippetOf = (snippetFindingId: number): SnippetSource => ({
  purl: `pkg:generic/snippet-${snippetFindingId}@1.0`,
  path: `snippet-${snippetFindingId}.c`,
  startLine: 5,
  endLine: 15,
  license: 'MIT',
  score: 9.5,
});

// Opens or closes the row with the given text through its button in the first column.
const toggleRow = async (
  user: ReturnType<typeof renderRunPage>['user'],
  text: string
) => {
  await user.click(
    screen.getByText(text).closest('tr')!.querySelector('td button')!
  );
};

const lastQuery = (mock: (typeof mocks)['getRunProvenanceSnippetFindings']) =>
  mock.mock.calls.at(-1)?.[0];

describe('snippet findings view', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    mocks.getRepositoryRun.mockResolvedValue({
      data: { id: runId, index: 4, jobs: { scanner: {} } },
    });
    mocks.getRunSnippetFindingProvenances.mockResolvedValue(
      pageOf(provenances)
    );
    mocks.getRunProvenanceSnippetFindings.mockImplementation(({ path }) =>
      Promise.resolve(pageOf(findingsOf(path.provenanceId), { totalCount: 12 }))
    );
    mocks.getRunSnippetFindingSnippets.mockImplementation(({ path }) =>
      Promise.resolve(pageOf([snippetOf(path.snippetFindingId)]))
    );
  });

  // The DataTableCards test asserts that rows open independently, so the single open row of
  // this view needs its own test.
  it('keeps at most one provenance open', async () => {
    const { user } = renderRunPage(SnippetFindingsView, 'snippet-findings');

    await screen.findByText('1 finding');
    await toggleRow(user, '1 finding');
    expect(await screen.findByText('src/1/a.c')).toBeVisible();

    await toggleRow(user, '2 findings');
    expect(await screen.findByText('src/2/c.c')).toBeVisible();
    expect(screen.queryByText('src/1/a.c')).not.toBeInTheDocument();

    await toggleRow(user, '2 findings');
    await waitFor(() =>
      expect(screen.queryByText('src/2/c.c')).not.toBeInTheDocument()
    );
  });

  it('returns the nested tables to their first page when opening a provenance', async () => {
    const { router, user } = renderRunPage(
      SnippetFindingsView,
      'snippet-findings',
      '?findingsPage=3&snippetsPage=2'
    );

    await screen.findByText('1 finding');
    await toggleRow(user, '1 finding');

    await waitFor(() =>
      expect(router.state.location.search).toEqual({
        findingsPage: 1,
        snippetsPage: 1,
      })
    );
  });

  it('pages and sorts the findings of a provenance through the server', async () => {
    const { router, user } = renderRunPage(
      SnippetFindingsView,
      'snippet-findings',
      '?findingsPageSize=5&findingsSortBy=[{"id":"path","desc":true}]'
    );

    await screen.findByText('1 finding');
    await toggleRow(user, '1 finding');
    await screen.findByText('src/1/a.c');

    expect(lastQuery(mocks.getRunProvenanceSnippetFindings)).toEqual(
      expect.objectContaining({
        path: { runId, provenanceId: 1 },
        query: { limit: 5, offset: 0, sort: '-path' },
      })
    );

    // The cell of the open provenance, which holds the findings table and its pagination.
    const findings = within(
      screen.getByText('src/1/a.c').closest('table')!.closest('td')!
    );
    await user.click(findings.getByRole('link', { name: 'Go to next page' }));

    await waitFor(() =>
      expect(lastQuery(mocks.getRunProvenanceSnippetFindings)).toEqual(
        expect.objectContaining({
          query: { limit: 5, offset: 5, sort: '-path' },
        })
      )
    );
    expect(router.state.location.search).toMatchObject({ findingsPage: 2 });

    // Sorting by path in descending order is the last step, so the next one removes it.
    await user.click(
      within(findings.getByRole('columnheader', { name: 'Path' })).getByRole(
        'link'
      )
    );

    await waitFor(() =>
      expect(lastQuery(mocks.getRunProvenanceSnippetFindings)).toEqual(
        expect.objectContaining({
          query: { limit: 5, offset: 5, sort: undefined },
        })
      )
    );
    expect(router.state.location.search).not.toHaveProperty('findingsSortBy');
  });

  it('loads the snippets of a finding only once it is opened', async () => {
    const { user } = renderRunPage(
      SnippetFindingsView,
      'snippet-findings',
      '?snippetsPageSize=5&snippetsSortBy=[{"id":"score","desc":true}]'
    );

    await screen.findByText('1 finding');
    await toggleRow(user, '1 finding');
    await screen.findByText('src/1/a.c');
    expect(mocks.getRunSnippetFindingSnippets).not.toHaveBeenCalled();

    await toggleRow(user, 'src/1/a.c');

    expect(await screen.findByText('pkg:generic/snippet-11@1.0')).toBeVisible();
    expect(screen.getByText('9.5')).toBeVisible();
    expect(screen.getByText('MIT')).toBeVisible();
    expect(mocks.getRunSnippetFindingSnippets).toHaveBeenCalledWith(
      expect.objectContaining({
        path: { runId, snippetFindingId: 11 },
        query: { limit: 5, offset: 0, sort: '-score' },
      })
    );
  });

  it('keeps at most one finding open and returns its snippets to the first page', async () => {
    const { router, user } = renderRunPage(
      SnippetFindingsView,
      'snippet-findings'
    );

    await screen.findByText('1 finding');
    await toggleRow(user, '1 finding');
    await screen.findByText('src/1/a.c');
    await toggleRow(user, 'src/1/a.c');
    await screen.findByText('pkg:generic/snippet-11@1.0');

    await act(() =>
      router.navigate({
        to: '.',
        search: (previous) => ({ ...previous, snippetsPage: 2 }),
      })
    );
    await toggleRow(user, 'src/1/b.c');

    expect(await screen.findByText('pkg:generic/snippet-12@1.0')).toBeVisible();
    expect(
      screen.queryByText('pkg:generic/snippet-11@1.0')
    ).not.toBeInTheDocument();
    expect(router.state.location.search).toMatchObject({ snippetsPage: 1 });
  });

  it('explains that there are no snippets if the run has no scanner job', async () => {
    mocks.getRepositoryRun.mockResolvedValue({
      data: { id: runId, index: 4, jobs: {} },
    });
    mocks.getRunSnippetFindingProvenances.mockResolvedValue(pageOf([]));

    renderRunPage(SnippetFindingsView, 'snippet-findings');

    expect(
      await screen.findByText(
        'No detected snippets are available because the scanner job was not enabled for this run.'
      )
    ).toBeVisible();
  });
});
