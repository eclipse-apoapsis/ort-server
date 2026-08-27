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

import { zRepositoryType } from '@/api/zod.gen';
import {
  REPOSITORY_ACCESS_SERVICE,
  REPOSITORY_PASSWORD_SECRET,
  REPOSITORY_USER_SECRET,
} from '@/lib/constants';
import {
  createRepositoryCredentials,
  getCredentialsTypes,
} from '@/lib/repository-credentials';

const mocks = vi.hoisted(() => ({
  calls: [] as string[],
  postRepositoryInfrastructureService: vi.fn(async () => {
    mocks.calls.push('infrastructure-service');
  }),
  postRepositorySecret: vi.fn(async () => {
    mocks.calls.push('secret');
  }),
}));

vi.mock('@/api/sdk.gen', () => ({
  postRepositoryInfrastructureService:
    mocks.postRepositoryInfrastructureService,
  postRepositorySecret: mocks.postRepositorySecret,
}));

const options = {
  repositoryId: 42,
  url: 'https://example.org/repository.git',
  type: 'GIT' as const,
  username: 'jdoe',
  password: 'token',
};

describe('repository credentials', () => {
  beforeEach(() => {
    mocks.calls = [];
    vi.clearAllMocks();
  });

  it('uses credential names accepted by the API', () => {
    expect([
      REPOSITORY_USER_SECRET,
      REPOSITORY_PASSWORD_SECRET,
      REPOSITORY_ACCESS_SERVICE,
    ]).toEqual([
      'REPOSITORY-USERNAME',
      'REPOSITORY-PASSWORD',
      'REPOSITORY-ACCESS',
    ]);
  });

  it('maps every repository type to its credentials type', () => {
    const credentialsTypes = Object.fromEntries(
      Object.values(zRepositoryType.enum).map((type) => [
        type,
        getCredentialsTypes(type),
      ])
    );

    expect(credentialsTypes).toEqual({
      GIT: ['GIT_CREDENTIALS_FILE'],
      GIT_REPO: [],
      MERCURIAL: [],
      SUBVERSION: [],
    });
  });

  it('creates the secrets before the infrastructure service', async () => {
    await createRepositoryCredentials(options);

    expect(mocks.calls).toEqual(['secret', 'secret', 'infrastructure-service']);

    expect(mocks.postRepositorySecret).toHaveBeenNthCalledWith(1, {
      path: { repositoryId: options.repositoryId },
      body: {
        name: REPOSITORY_USER_SECRET,
        value: options.username,
        description: 'The username for accessing this repository.',
      },
      throwOnError: true,
    });
    expect(mocks.postRepositorySecret).toHaveBeenNthCalledWith(2, {
      path: { repositoryId: options.repositoryId },
      body: {
        name: REPOSITORY_PASSWORD_SECRET,
        value: options.password,
        description:
          'The password or personal access token for accessing this repository.',
      },
      throwOnError: true,
    });
    expect(mocks.postRepositoryInfrastructureService).toHaveBeenCalledWith({
      path: { repositoryId: options.repositoryId },
      body: {
        name: REPOSITORY_ACCESS_SERVICE,
        url: options.url,
        description: 'Access to the source code of this repository.',
        usernameSecretRef: REPOSITORY_USER_SECRET,
        passwordSecretRef: REPOSITORY_PASSWORD_SECRET,
        credentialsTypes: ['GIT_CREDENTIALS_FILE'],
      },
      throwOnError: true,
    });
  });

  it('stops if creating the first secret fails', async () => {
    const error = new Error('Could not create the user secret.');
    mocks.postRepositorySecret.mockRejectedValueOnce(error);

    await expect(createRepositoryCredentials(options)).rejects.toBe(error);

    expect(mocks.postRepositorySecret).toHaveBeenCalledTimes(1);
    expect(mocks.postRepositoryInfrastructureService).not.toHaveBeenCalled();
  });

  it('keeps the secrets if creating the infrastructure service fails', async () => {
    const error = new Error('Could not create the infrastructure service.');
    mocks.postRepositoryInfrastructureService.mockRejectedValueOnce(error);

    await expect(createRepositoryCredentials(options)).rejects.toBe(error);

    expect(mocks.postRepositorySecret).toHaveBeenCalledTimes(2);
    expect(mocks.postRepositoryInfrastructureService).toHaveBeenCalledOnce();
  });
});
