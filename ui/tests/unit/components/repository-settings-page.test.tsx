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

import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { Repository } from '@/api';
import type { RepositoryDeletionProgress } from '@/lib/repository-deletion';
import { RepositorySettingsPage } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/settings';

const mocks = vi.hoisted(() => ({
  deleteRepositoryWithContents: vi.fn(),
  getRepositoryInfrastructureServicesQueryKey: vi.fn(() => [
    'repository-infrastructure-services',
  ]),
  getRepositorySecretsQueryKey: vi.fn(() => ['repository-secrets']),
  invalidateQueries: vi.fn(),
  mutateAsync: vi.fn(),
  navigate: vi.fn(),
  repositoryDeleted: vi.fn(),
  repositoryUpdated: vi.fn(),
  routerInvalidate: vi.fn(),
  toastError: vi.fn(),
  toastInfo: vi.fn(),
}));

vi.mock('@tanstack/react-query', () => ({
  useMutation: () => ({ mutateAsync: mocks.mutateAsync, isPending: false }),
  useQueryClient: () => ({ invalidateQueries: mocks.invalidateQueries }),
  useSuspenseQuery: () => ({ data: repository }),
}));

vi.mock('@tanstack/react-router', () => ({
  createFileRoute: () => (options: Record<string, unknown>) => ({
    ...options,
    useParams: () => ({ orgId: '1', productId: '2', repoId: '42' }),
  }),
  useNavigate: () => mocks.navigate,
  useRouter: () => ({ invalidate: mocks.routerInvalidate }),
}));

vi.mock('@/api/@tanstack/react-query.gen', () => ({
  getRepositoryInfrastructureServicesQueryKey:
    mocks.getRepositoryInfrastructureServicesQueryKey,
  getRepositoryOptions: () => ({}),
  getRepositorySecretsQueryKey: mocks.getRepositorySecretsQueryKey,
  patchRepositoryMutation: () => ({}),
}));

vi.mock('@/lib/entity-cache', () => ({
  repositoryDeleted: mocks.repositoryDeleted,
  repositoryUpdated: mocks.repositoryUpdated,
}));

vi.mock('@/lib/repository-deletion', () => ({
  deleteRepositoryWithContents: mocks.deleteRepositoryWithContents,
}));

vi.mock('@/lib/toast', () => ({
  toast: { info: mocks.toastInfo },
  toastError: mocks.toastError,
}));

vi.mock(
  '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/settings/-components/edit-repository-form',
  () => ({ EditRepositoryForm: () => <div>Edit repository</div> })
);

vi.mock(
  '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/-components',
  () => ({ MoveRepository: () => <div>Move repository</div> })
);

const repository: Repository = {
  id: 42,
  organizationId: 1,
  productId: 2,
  type: 'GIT',
  url: 'https://example.org/repository.git',
};

const dialogDescription =
  'This deletes the repository together with all its ORT runs and their results, its secrets and its infrastructure services. Deletion is irreversible.';

type ProgressCallback = (progress: RepositoryDeletionProgress) => void;

function completeDeletion() {
  mocks.deleteRepositoryWithContents.mockImplementation(
    async (_repositoryId: number, onProgress: ProgressCallback) => {
      onProgress({ phase: 'infrastructure-services', deletedCount: 3 });
      onProgress({ phase: 'secrets', deletedCount: 5 });
      onProgress({ phase: 'repository', deletedCount: 1 });
    }
  );
}

async function confirmDeletion(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('button', { name: 'Delete repository' }));
  await user.type(screen.getByRole('textbox'), repository.url);
  await user.click(screen.getByRole('button', { name: 'Delete' }));
}

describe('RepositorySettingsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.deleteRepositoryWithContents.mockResolvedValue(undefined);
  });

  it('describes all repository contents that will be deleted', async () => {
    const user = userEvent.setup();
    render(<RepositorySettingsPage />);

    await user.click(screen.getByRole('button', { name: 'Delete repository' }));

    expect(screen.getByText(dialogDescription)).toBeVisible();
  });

  it('reports each completed non-empty deletion phase once', async () => {
    const user = userEvent.setup();
    completeDeletion();
    render(<RepositorySettingsPage />);

    await confirmDeletion(user);

    await waitFor(() =>
      expect(mocks.deleteRepositoryWithContents).toHaveBeenCalledOnce()
    );
    expect(mocks.toastInfo.mock.calls).toEqual([
      [
        'Delete Infrastructure Services',
        {
          description: `Deleted 3 infrastructure services from repository "${repository.url}".`,
        },
      ],
      [
        'Delete Secrets',
        {
          description: `Deleted 5 secrets from repository "${repository.url}".`,
        },
      ],
      [
        'Delete Repository',
        {
          description: `Repository "${repository.url}" deleted successfully.`,
        },
      ],
    ]);
  });

  it('updates the hierarchy cache and navigates after deletion', async () => {
    const user = userEvent.setup();
    completeDeletion();
    render(<RepositorySettingsPage />);

    await confirmDeletion(user);

    await waitFor(() =>
      expect(mocks.repositoryDeleted).toHaveBeenCalledWith(
        expect.objectContaining({ invalidateQueries: mocks.invalidateQueries }),
        repository
      )
    );
    expect(mocks.navigate).toHaveBeenCalledWith({
      to: '/organizations/$orgId/products/$productId',
      params: { orgId: '1', productId: '2' },
    });
  });

  it('reports the failed phase without navigating', async () => {
    const user = userEvent.setup();
    const error = new Error('Could not delete a secret.');
    mocks.deleteRepositoryWithContents.mockImplementation(
      async (_repositoryId: number, onProgress: ProgressCallback) => {
        onProgress({ phase: 'infrastructure-services', deletedCount: 2 });
        throw error;
      }
    );
    render(<RepositorySettingsPage />);

    await confirmDeletion(user);

    await waitFor(() =>
      expect(mocks.toastError).toHaveBeenCalledWith(
        `Could not delete the secrets of repository "${repository.url}". ` +
          'Some infrastructure services and secrets may already have been deleted. ' +
          'Retry the deletion to remove the rest.',
        error
      )
    );
    expect(mocks.toastError).toHaveBeenCalledOnce();
    expect(mocks.repositoryDeleted).not.toHaveBeenCalled();
    expect(mocks.navigate).not.toHaveBeenCalled();
  });

  it('invalidates cleanup queries after a failure', async () => {
    const user = userEvent.setup();
    mocks.deleteRepositoryWithContents.mockRejectedValue(
      new Error('Could not delete an infrastructure service.')
    );
    render(<RepositorySettingsPage />);

    await confirmDeletion(user);

    await waitFor(() =>
      expect(mocks.invalidateQueries).toHaveBeenCalledTimes(2)
    );
    expect(
      mocks.getRepositoryInfrastructureServicesQueryKey
    ).toHaveBeenCalledWith({ path: { repositoryId: repository.id } });
    expect(mocks.getRepositorySecretsQueryKey).toHaveBeenCalledWith({
      path: { repositoryId: repository.id },
    });
    expect(mocks.invalidateQueries.mock.calls).toEqual([
      [{ queryKey: ['repository-infrastructure-services'] }],
      [{ queryKey: ['repository-secrets'] }],
    ]);
  });
});
