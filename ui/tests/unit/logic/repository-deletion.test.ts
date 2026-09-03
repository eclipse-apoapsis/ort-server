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

import { beforeEach, describe, expect, it, vi } from 'vitest';

import { deleteRepositoryWithContents } from '@/lib/repository-deletion';

const mocks = vi.hoisted(() => ({
  calls: [] as string[],
  infrastructureServices: [] as Array<{ name: string }>,
  secrets: [] as Array<{ name: string }>,
  deleteRepository: vi.fn(),
  deleteRepositoryInfrastructureService: vi.fn(),
  deleteRepositorySecret: vi.fn(),
  getRepositoryInfrastructureServices: vi.fn(),
  getRepositorySecrets: vi.fn(),
}));

vi.mock('@/api/sdk.gen', () => ({
  deleteRepository: mocks.deleteRepository,
  deleteRepositoryInfrastructureService:
    mocks.deleteRepositoryInfrastructureService,
  deleteRepositorySecret: mocks.deleteRepositorySecret,
  getRepositoryInfrastructureServices:
    mocks.getRepositoryInfrastructureServices,
  getRepositorySecrets: mocks.getRepositorySecrets,
}));

const repositoryId = 42;
const pageSize = 100;

function createPage<T>(items: T[], offset: number) {
  return {
    data: items.slice(offset, offset + pageSize),
    pagination: {
      limit: pageSize,
      offset,
      sortProperties: [],
      totalCount: items.length,
    },
  };
}

describe('repository deletion', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.calls = [];
    mocks.infrastructureServices = [
      { name: 'infrastructure-service-1' },
      { name: 'infrastructure-service-2' },
    ];
    mocks.secrets = [{ name: 'secret-1' }, { name: 'secret-2' }];

    mocks.getRepositoryInfrastructureServices.mockImplementation(
      async ({ query: { offset } }) => {
        mocks.calls.push(`list-infrastructure-services:${offset}`);
        return { data: createPage(mocks.infrastructureServices, offset) };
      }
    );
    mocks.deleteRepositoryInfrastructureService.mockImplementation(
      async ({ path: { serviceName } }) => {
        mocks.calls.push(`delete-infrastructure-service:${serviceName}`);
      }
    );
    mocks.getRepositorySecrets.mockImplementation(
      async ({ query: { offset } }) => {
        mocks.calls.push(`list-secrets:${offset}`);
        return { data: createPage(mocks.secrets, offset) };
      }
    );
    mocks.deleteRepositorySecret.mockImplementation(
      async ({ path: { secretName } }) => {
        mocks.calls.push(`delete-secret:${secretName}`);
      }
    );
    mocks.deleteRepository.mockImplementation(async () => {
      mocks.calls.push('delete-repository');
    });
  });

  it('deletes infrastructure services before secrets and the repository', async () => {
    await deleteRepositoryWithContents(repositoryId, vi.fn());

    expect(mocks.calls).toEqual([
      'list-infrastructure-services:0',
      'delete-infrastructure-service:infrastructure-service-1',
      'delete-infrastructure-service:infrastructure-service-2',
      'list-secrets:0',
      'delete-secret:secret-1',
      'delete-secret:secret-2',
      'delete-repository',
    ]);
    expect(mocks.getRepositoryInfrastructureServices).toHaveBeenCalledWith({
      path: { repositoryId },
      query: { limit: pageSize, offset: 0 },
      throwOnError: true,
    });
    expect(mocks.deleteRepositoryInfrastructureService).toHaveBeenCalledWith({
      path: {
        repositoryId,
        serviceName: 'infrastructure-service-1',
      },
      throwOnError: true,
    });
    expect(mocks.getRepositorySecrets).toHaveBeenCalledWith({
      path: { repositoryId },
      query: { limit: pageSize, offset: 0 },
      throwOnError: true,
    });
    expect(mocks.deleteRepositorySecret).toHaveBeenCalledWith({
      path: { repositoryId, secretName: 'secret-1' },
      throwOnError: true,
    });
    expect(mocks.deleteRepository).toHaveBeenCalledWith({
      path: { repositoryId },
      throwOnError: true,
    });
  });

  it('reads and deletes all pages before moving to the next phase', async () => {
    mocks.infrastructureServices = Array.from({ length: 201 }, (_, index) => ({
      name: `infrastructure-service-${index}`,
    }));
    mocks.secrets = Array.from({ length: 201 }, (_, index) => ({
      name: `secret-${index}`,
    }));

    await deleteRepositoryWithContents(repositoryId, vi.fn());

    expect(
      mocks.getRepositoryInfrastructureServices.mock.calls.map(
        ([{ query }]) => query.offset
      )
    ).toEqual([0, 100, 200]);
    expect(mocks.deleteRepositoryInfrastructureService).toHaveBeenCalledTimes(
      201
    );
    expect(
      mocks.getRepositorySecrets.mock.calls.map(([{ query }]) => query.offset)
    ).toEqual([0, 100, 200]);
    expect(mocks.deleteRepositorySecret).toHaveBeenCalledTimes(201);

    const firstSecretList = mocks.calls.indexOf('list-secrets:0');
    expect(
      mocks.calls
        .slice(0, firstSecretList)
        .filter((call) => call.startsWith('delete-infrastructure-service:'))
    ).toHaveLength(201);
  });

  it('reports each completed phase once', async () => {
    const onProgress = vi.fn();

    await deleteRepositoryWithContents(repositoryId, onProgress);

    expect(onProgress.mock.calls).toEqual([
      [{ phase: 'infrastructure-services', deletedCount: 2 }],
      [{ phase: 'secrets', deletedCount: 2 }],
      [{ phase: 'repository', deletedCount: 1 }],
    ]);
  });

  it('deletes the repository when its cleanup phases are empty', async () => {
    mocks.infrastructureServices = [];
    mocks.secrets = [];
    const onProgress = vi.fn();

    await deleteRepositoryWithContents(repositoryId, onProgress);

    expect(mocks.deleteRepositoryInfrastructureService).not.toHaveBeenCalled();
    expect(mocks.deleteRepositorySecret).not.toHaveBeenCalled();
    expect(mocks.deleteRepository).toHaveBeenCalledOnce();
    expect(onProgress.mock.calls).toEqual([
      [{ phase: 'infrastructure-services', deletedCount: 0 }],
      [{ phase: 'secrets', deletedCount: 0 }],
      [{ phase: 'repository', deletedCount: 1 }],
    ]);
  });

  it('stops when deleting an infrastructure service fails', async () => {
    const error = new Error('Could not delete an infrastructure service.');
    mocks.deleteRepositoryInfrastructureService
      .mockResolvedValueOnce(undefined)
      .mockRejectedValueOnce(error);
    const onProgress = vi.fn();

    await expect(
      deleteRepositoryWithContents(repositoryId, onProgress)
    ).rejects.toBe(error);

    expect(mocks.getRepositorySecrets).not.toHaveBeenCalled();
    expect(mocks.deleteRepositorySecret).not.toHaveBeenCalled();
    expect(mocks.deleteRepository).not.toHaveBeenCalled();
    expect(onProgress).not.toHaveBeenCalled();
  });

  it('stops when deleting a secret fails', async () => {
    const error = new Error('Could not delete a secret.');
    mocks.deleteRepositorySecret
      .mockResolvedValueOnce(undefined)
      .mockRejectedValueOnce(error);
    const onProgress = vi.fn();

    await expect(
      deleteRepositoryWithContents(repositoryId, onProgress)
    ).rejects.toBe(error);

    expect(mocks.deleteRepository).not.toHaveBeenCalled();
    expect(onProgress.mock.calls).toEqual([
      [{ phase: 'infrastructure-services', deletedCount: 2 }],
    ]);
  });

  it('does not report a phase that fails while listing its items', async () => {
    const error = new Error('Could not list secrets.');
    mocks.getRepositorySecrets.mockRejectedValueOnce(error);
    const onProgress = vi.fn();

    await expect(
      deleteRepositoryWithContents(repositoryId, onProgress)
    ).rejects.toBe(error);

    expect(mocks.deleteRepositorySecret).not.toHaveBeenCalled();
    expect(mocks.deleteRepository).not.toHaveBeenCalled();
    expect(onProgress.mock.calls).toEqual([
      [{ phase: 'infrastructure-services', deletedCount: 2 }],
    ]);
  });

  it('can retry after a partial failure and delete the remaining items', async () => {
    let shouldFail = true;

    mocks.deleteRepositoryInfrastructureService.mockImplementation(
      async ({ path: { serviceName } }) => {
        const index = mocks.infrastructureServices.findIndex(
          ({ name }) => name === serviceName
        );
        mocks.infrastructureServices.splice(index, 1);
      }
    );
    mocks.deleteRepositorySecret.mockImplementation(
      async ({ path: { secretName } }) => {
        if (secretName === 'secret-2' && shouldFail) {
          shouldFail = false;
          throw new Error('Could not delete the secret.');
        }

        const index = mocks.secrets.findIndex(
          ({ name }) => name === secretName
        );
        mocks.secrets.splice(index, 1);
      }
    );

    const firstProgress = vi.fn();
    await expect(
      deleteRepositoryWithContents(repositoryId, firstProgress)
    ).rejects.toThrow('Could not delete the secret.');

    expect(mocks.infrastructureServices).toEqual([]);
    expect(mocks.secrets).toEqual([{ name: 'secret-2' }]);
    expect(firstProgress.mock.calls).toEqual([
      [{ phase: 'infrastructure-services', deletedCount: 2 }],
    ]);

    const retryProgress = vi.fn();
    await deleteRepositoryWithContents(repositoryId, retryProgress);

    expect(mocks.secrets).toEqual([]);
    expect(mocks.deleteRepository).toHaveBeenCalledOnce();
    expect(retryProgress.mock.calls).toEqual([
      [{ phase: 'infrastructure-services', deletedCount: 0 }],
      [{ phase: 'secrets', deletedCount: 1 }],
      [{ phase: 'repository', deletedCount: 1 }],
    ]);
  });
});
