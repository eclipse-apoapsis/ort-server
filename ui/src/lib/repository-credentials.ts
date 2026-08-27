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

import type { CredentialsType, Repository, RepositoryType } from '@/api';
import {
  postRepository,
  postRepositoryInfrastructureService,
  postRepositorySecret,
} from '@/api/sdk.gen';
import {
  REPOSITORY_ACCESS_SERVICE,
  REPOSITORY_PASSWORD_SECRET,
  REPOSITORY_USER_SECRET,
} from '@/lib/constants';
import type { CreateRepositoryFormValues } from '@/schemas';

export function getCredentialsTypes(type: RepositoryType): CredentialsType[] {
  return type === 'GIT' ? ['GIT_CREDENTIALS_FILE'] : [];
}

type CreateRepositoryCredentialsOptions = {
  repositoryId: number;
  url: string;
  type: RepositoryType;
  username: string;
  password: string;
};

export async function createRepositoryCredentials({
  repositoryId,
  url,
  type,
  username,
  password,
}: CreateRepositoryCredentialsOptions): Promise<void> {
  const path = { repositoryId };

  await postRepositorySecret({
    path,
    body: {
      name: REPOSITORY_USER_SECRET,
      value: username,
      description: 'The username for accessing this repository.',
    },
    throwOnError: true,
  });

  await postRepositorySecret({
    path,
    body: {
      name: REPOSITORY_PASSWORD_SECRET,
      value: password,
      description:
        'The password or personal access token for accessing this repository.',
    },
    throwOnError: true,
  });

  await postRepositoryInfrastructureService({
    path,
    body: {
      name: REPOSITORY_ACCESS_SERVICE,
      url,
      description: 'Access to the source code of this repository.',
      usernameSecretRef: REPOSITORY_USER_SECRET,
      passwordSecretRef: REPOSITORY_PASSWORD_SECRET,
      credentialsTypes: getCredentialsTypes(type),
    },
    throwOnError: true,
  });
}

type CreateRepositoryAndCredentialsOptions = {
  productId: number;
  values: CreateRepositoryFormValues;
};

export type CreateRepositoryResult = {
  repository: Repository;
  credentialsError?: unknown;
};

export async function createRepositoryAndCredentials({
  productId,
  values,
}: CreateRepositoryAndCredentialsOptions): Promise<CreateRepositoryResult> {
  const { username, password, ...body } = values;

  const { data: repository } = await postRepository({
    path: { productId },
    body,
    throwOnError: true,
  });

  if (!username) return { repository };

  try {
    await createRepositoryCredentials({
      repositoryId: repository.id,
      url: repository.url,
      type: repository.type,
      username,
      password,
    });

    return { repository };
  } catch (error) {
    // The repository exists; only its credentials are incomplete, which is reported and not undone.
    return { repository, credentialsError: error };
  }
}
