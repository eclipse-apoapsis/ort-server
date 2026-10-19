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

import type { QueryClient, QueryKey } from '@tanstack/react-query';
import { screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type {
  AppliedVulnerabilityResolution,
  IssueResolutionReason,
  ResolutionSource,
  RuleViolationResolutionReason,
} from '@/api';
import {
  getRunIssuesQueryKey,
  getRunRuleViolationsQueryKey,
  getRunVulnerabilitiesQueryKey,
} from '@/api/@tanstack/react-query.gen';
import { Resolutions } from '@/components/resolutions';
import { TooltipProvider } from '@/components/ui/tooltip';
import type { ItemWithResolutions } from '@/helpers/resolutions';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';
import {
  createIssue,
  createRuleViolation,
  createVulnerability,
} from '../fixtures/resolutions';

const mocks = vi.hoisted(() => ({
  postIssueResolutionForRepository: vi.fn(),
  patchIssueResolutionForRepository: vi.fn(),
  deleteIssueResolutionForRepository: vi.fn(),
  postRuleViolationResolutionForRepository: vi.fn(),
  patchRuleViolationResolutionForRepository: vi.fn(),
  deleteRuleViolationResolutionForRepository: vi.fn(),
  postVulnerabilityResolution: vi.fn(),
  patchVulnerabilityResolution: vi.fn(),
  deleteVulnerabilityResolution: vi.fn(),
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  ...mocks,
}));

vi.mock('@/lib/toast', () => ({
  toast: { info: vi.fn() },
  toastError: vi.fn(),
}));

const RUN_ID = 9;

const runItemKeys = {
  issue: getRunIssuesQueryKey({ path: { runId: RUN_ID } }),
  'rule violation': getRunRuleViolationsQueryKey({ path: { runId: RUN_ID } }),
  vulnerability: getRunVulnerabilitiesQueryKey({ path: { runId: RUN_ID } }),
};

type ItemType = keyof typeof runItemKeys;

// A resolution of an issue or rule violation, by default one the server manages. Its message is a
// pattern that differs from the message of the item, so a request that uses the message of the
// item instead of the hash of the resolution addresses the wrong one.
const serverMessageResolution = <
  TReason extends IssueResolutionReason | RuleViolationResolutionReason,
>(
  reason: TReason,
  source: ResolutionSource = 'SERVER'
) => ({
  source,
  reason,
  message: 'message.*',
  messageHash: 'hash-1',
  comment: 'Stored comment',
  isDeleted: false,
});

// Resolutions also come from two files that the server does not manage: the global resolutions
// file and the configuration file of the repository.
const fileSources = ['GLOBAL_FILE', 'REPOSITORY_FILE'] as const;

type FileSource = (typeof fileSources)[number];

const serverVulnerabilityResolution: AppliedVulnerabilityResolution = {
  source: 'SERVER',
  externalId: 'CVE-0000-0000',
  reason: 'CANT_FIX_VULNERABILITY',
  comment: 'Stored comment',
  isDeleted: false,
};

const itemTypes = [
  {
    type: 'issue' as ItemType,
    label: 'an issue',
    unresolved: () => createIssue(),
    resolved: () =>
      createIssue({
        resolutions: [serverMessageResolution('CANT_FIX_ISSUE')],
      }),
    fromFile: (source: FileSource) =>
      createIssue({
        resolutions: [serverMessageResolution('CANT_FIX_ISSUE', source)],
      }),
    reason: 'SCANNER_ISSUE',
    create: mocks.postIssueResolutionForRepository,
    createPath: { repositoryId: 3 },
    createBody: { message: 'Issue message' },
    patch: mocks.patchIssueResolutionForRepository,
    remove: mocks.deleteIssueResolutionForRepository,
    resolutionPath: { repositoryId: 3, messageHash: 'hash-1' },
  },
  {
    type: 'rule violation' as ItemType,
    label: 'a rule violation',
    unresolved: () => createRuleViolation(),
    resolved: () =>
      createRuleViolation({
        resolutions: [serverMessageResolution('CANT_FIX_EXCEPTION')],
      }),
    fromFile: (source: FileSource) =>
      createRuleViolation({
        resolutions: [serverMessageResolution('CANT_FIX_EXCEPTION', source)],
      }),
    reason: 'NOT_MODIFIED_EXCEPTION',
    create: mocks.postRuleViolationResolutionForRepository,
    createPath: { repositoryId: 3 },
    createBody: { message: 'Rule violation message' },
    patch: mocks.patchRuleViolationResolutionForRepository,
    remove: mocks.deleteRuleViolationResolutionForRepository,
    resolutionPath: { repositoryId: 3, messageHash: 'hash-1' },
  },
  {
    type: 'vulnerability' as ItemType,
    label: 'a vulnerability',
    unresolved: () =>
      createVulnerability({ resolutions: [], unappliedResolutions: [] }),
    resolved: () =>
      createVulnerability({
        resolutions: [serverVulnerabilityResolution],
        unappliedResolutions: [],
      }),
    fromFile: (source: FileSource) =>
      createVulnerability({
        resolutions: [{ ...serverVulnerabilityResolution, source }],
        unappliedResolutions: [],
      }),
    reason: 'NOT_A_VULNERABILITY',
    create: mocks.postVulnerabilityResolution,
    createPath: { repositoryId: 3, externalId: 'CVE-0000-0000' },
    createBody: {},
    patch: mocks.patchVulnerabilityResolution,
    remove: mocks.deleteVulnerabilityResolution,
    resolutionPath: { repositoryId: 3, externalId: 'CVE-0000-0000' },
  },
];

const renderResolutions = (
  item: ItemWithResolutions,
  { repositoryId = '3' }: { repositoryId?: string } = {}
) => {
  const result = renderInteractiveWithRouter(
    <TooltipProvider>
      <Resolutions item={item} repositoryId={repositoryId} runId={RUN_ID} />
    </TooltipProvider>,
    { path: '/', withQueryClient: true }
  );
  const queryClient = result.queryClient!;

  // Put the items of the run in the cache, so that there are queries to invalidate.
  for (const key of Object.values(runItemKeys)) {
    queryClient.setQueryData(key, { seeded: true });
  }

  return { ...result, queryClient };
};

const isInvalidated = (queryClient: QueryClient, key: QueryKey) =>
  queryClient.getQueryState(key)?.isInvalidated;

// Which items of the run are refreshed, by type.
const invalidatedRunItems = (queryClient: QueryClient) =>
  Object.fromEntries(
    Object.entries(runItemKeys).map(([type, key]) => [
      type,
      isInvalidated(queryClient, key),
    ])
  );

const onlyInvalidated = (type: ItemType) =>
  Object.fromEntries(
    Object.keys(runItemKeys).map((other) => [other, other === type])
  );

// The card that shows the resolution with the given reason.
const findResolutionCard = async (reason: string) =>
  within(
    (
      await screen.findByText(reason, {
        selector: '[data-slot="card-title"] span',
      })
    ).closest<HTMLElement>('[data-slot="card"]')!
  );

describe('Resolutions', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    for (const mutation of Object.values(mocks)) {
      mutation.mockResolvedValue({ data: undefined });
    }
  });

  it.each(itemTypes)(
    'creates a resolution of $label',
    async ({ type, unresolved, reason, create, createPath, createBody }) => {
      const { queryClient, user } = renderResolutions(unresolved());

      await user.click(await screen.findByRole('combobox'));
      await user.click(await screen.findByRole('option', { name: reason }));
      await user.type(screen.getByLabelText('Comment'), 'A comment');
      await user.click(
        screen.getByRole('button', { name: 'Create resolution' })
      );

      await waitFor(() => expect(create).toHaveBeenCalledOnce());
      expect(create).toHaveBeenCalledWith(
        expect.objectContaining({
          path: createPath,
          body: { comment: 'A comment', reason, ...createBody },
        })
      );
      await waitFor(() =>
        expect(invalidatedRunItems(queryClient)).toEqual(onlyInvalidated(type))
      );
    }
  );

  it.each(itemTypes)(
    'edits a resolution of $label',
    async ({ type, resolved, patch, resolutionPath }) => {
      const item = resolved();
      const { reason } = item.resolutions![0]!;
      const { queryClient, user } = renderResolutions(item);

      await user.click(
        (await screen.findAllByRole('button', { name: '' }))[0]!
      );
      const comment = screen.getByLabelText('Comment');
      await user.clear(comment);
      await user.type(comment, 'Updated comment');
      await user.click(screen.getByRole('button', { name: 'Save' }));

      await waitFor(() =>
        expect(
          screen.queryByRole('button', { name: 'Save' })
        ).not.toBeInTheDocument()
      );
      expect(patch).toHaveBeenCalledOnce();
      expect(patch).toHaveBeenCalledWith(
        expect.objectContaining({
          path: resolutionPath,
          body: { comment: 'Updated comment', reason },
        })
      );
      expect(invalidatedRunItems(queryClient)).toEqual(onlyInvalidated(type));
    }
  );

  it.each(itemTypes)(
    'deletes a resolution of $label',
    async ({ type, resolved, remove, resolutionPath }) => {
      const item = resolved();
      const { queryClient, user } = renderResolutions(item);

      await user.click(
        (await findResolutionCard(item.resolutions![0]!.reason)).getByRole(
          'button',
          { name: 'Delete' }
        )
      );
      const dialog = await screen.findByRole('alertdialog');
      await user.click(within(dialog).getByRole('button', { name: 'Delete' }));

      await waitFor(() => expect(remove).toHaveBeenCalledOnce());
      expect(remove).toHaveBeenCalledWith(
        expect.objectContaining({ path: resolutionPath })
      );
      await waitFor(() =>
        expect(invalidatedRunItems(queryClient)).toEqual(onlyInvalidated(type))
      );
    }
  );

  it.each(
    itemTypes.flatMap((itemType) =>
      fileSources.map((source) => ({ ...itemType, source }))
    )
  )(
    'does not offer to change a $source resolution of $label',
    async ({ fromFile, source }) => {
      renderResolutions(fromFile(source));

      expect(await screen.findByText(source)).toBeVisible();
      expect(screen.queryByRole('button')).not.toBeInTheDocument();
    }
  );

  it('marks a resolution that the next run applies', async () => {
    renderResolutions(
      createIssue({
        unappliedResolutions: [
          {
            source: 'SERVER',
            message: 'Issue message',
            messageHash: 'hash-2',
            reason: 'CANT_FIX_ISSUE',
            comment: 'New comment',
          },
        ],
      })
    );

    expect(await screen.findByText('Created: Pending rerun')).toBeVisible();
  });

  it('shows the resolutions read-only outside a repository', async () => {
    renderResolutions(
      createIssue({
        resolutions: [serverMessageResolution('CANT_FIX_ISSUE')],
      }),
      { repositoryId: '' }
    );

    expect(await screen.findByText('CANT_FIX_ISSUE')).toBeVisible();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
  });
});
