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

import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { Repository } from '@/api';
import type { ApiError } from '@/lib/api-error';
import type { CreateRepositoryResult } from '@/lib/repository-credentials';
import { CreateRepositoryPage } from '@/routes/organizations/$orgId/products/$productId/create-repository';

const mocks = vi.hoisted(() => ({
  mutationOptions: undefined as unknown,
  mutateAsync: vi.fn(),
  navigate: vi.fn(),
  queryClient: {},
  repositoryCreated: vi.fn(),
  toastError: vi.fn(),
  toastInfo: vi.fn(),
}));

vi.mock('@tanstack/react-query', () => ({
  useMutation: (options: unknown) => {
    mocks.mutationOptions = options;
    return { mutateAsync: mocks.mutateAsync, isPending: false };
  },
  useQueryClient: () => mocks.queryClient,
}));

vi.mock('@tanstack/react-router', () => ({
  createFileRoute: () => (options: Record<string, unknown>) => ({
    ...options,
    useParams: () => ({ orgId: '1', productId: '2' }),
  }),
  useNavigate: () => mocks.navigate,
}));

vi.mock('@/lib/entity-cache', () => ({
  repositoryCreated: mocks.repositoryCreated,
}));

vi.mock('@/lib/toast', () => ({
  toast: { info: mocks.toastInfo },
  toastError: mocks.toastError,
}));

type MutationOptions = {
  onSuccess: (result: CreateRepositoryResult) => void;
  onError: (error: ApiError) => void;
};

const repository: Repository = {
  id: 42,
  organizationId: 1,
  productId: 2,
  type: 'GIT',
  url: 'https://example.org/repository.git',
};

const getMutationOptions = () => mocks.mutationOptions as MutationOptions;

describe('CreateRepositoryPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.mutationOptions = undefined;
    CreateRepositoryPage();
  });

  it('reports success and navigates to the repository', () => {
    getMutationOptions().onSuccess({ repository });

    expect(mocks.repositoryCreated).toHaveBeenCalledWith(
      mocks.queryClient,
      repository.productId
    );
    expect(mocks.toastInfo).toHaveBeenCalledWith('Add Repository', {
      description: `Repository ${repository.url} added successfully.`,
    });
    expect(mocks.navigate).toHaveBeenCalledWith({
      to: '/organizations/$orgId/products/$productId/repositories/$repoId',
      params: {
        orgId: '1',
        productId: '2',
        repoId: repository.id.toString(),
      },
    });
  });

  it('reports incomplete credentials and still navigates', () => {
    const error = new Error('Could not create the credentials.');

    getMutationOptions().onSuccess({ repository, credentialsError: error });

    expect(mocks.repositoryCreated).toHaveBeenCalledWith(
      mocks.queryClient,
      repository.productId
    );
    expect(mocks.toastError).toHaveBeenCalledWith(
      `Repository ${repository.url} was added, but its credentials could not be stored. ` +
        'Add the missing entries under the repository’s Secrets and Infrastructure Services.',
      error
    );
    expect(mocks.navigate).toHaveBeenCalledOnce();
  });

  it('reports a repository creation error without updating the cache', () => {
    const error = new Error('Could not create the repository.') as ApiError;

    getMutationOptions().onError(error);

    expect(mocks.toastError).toHaveBeenCalledWith(error.message, error);
    expect(mocks.repositoryCreated).not.toHaveBeenCalled();
    expect(mocks.navigate).not.toHaveBeenCalled();
  });
});
