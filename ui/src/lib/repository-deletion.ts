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

import type { InfrastructureService, Secret } from '@/api';
import {
  deleteRepository,
  deleteRepositoryInfrastructureService,
  deleteRepositorySecret,
  getRepositoryInfrastructureServices,
  getRepositorySecrets,
} from '@/api/sdk.gen';
import { getNextPageParam, type PagedResponse } from '@/lib/infinite-list';

const PAGE_SIZE = 100;

export type RepositoryDeletionPhase =
  'infrastructure-services' | 'secrets' | 'repository';

export type RepositoryDeletionProgress = {
  phase: RepositoryDeletionPhase;
  deletedCount: number;
};

type GetPage<TItem> = (offset: number) => Promise<PagedResponse<TItem>>;

async function getAllItems<TItem>(getPage: GetPage<TItem>) {
  const items: TItem[] = [];
  let offset: number | undefined = 0;

  while (offset !== undefined) {
    const page = await getPage(offset);
    items.push(...page.data);
    offset = getNextPageParam(page);
  }

  return items;
}

/**
 * Delete all infrastructure services and secrets of a repository before deleting the repository
 * itself. Items are read from every page before deletion so that removing them does not shift the
 * offsets of subsequent pages.
 */
export async function deleteRepositoryWithContents(
  repositoryId: number,
  onProgress: (progress: RepositoryDeletionProgress) => void
): Promise<void> {
  const path = { repositoryId };

  const infrastructureServices = await getAllItems<InfrastructureService>(
    async (offset) => {
      const response = await getRepositoryInfrastructureServices({
        path,
        query: { limit: PAGE_SIZE, offset },
        throwOnError: true,
      });

      return response.data;
    }
  );

  for (const service of infrastructureServices) {
    await deleteRepositoryInfrastructureService({
      path: { ...path, serviceName: service.name },
      throwOnError: true,
    });
  }

  onProgress({
    phase: 'infrastructure-services',
    deletedCount: infrastructureServices.length,
  });

  const secrets = await getAllItems<Secret>(async (offset) => {
    const response = await getRepositorySecrets({
      path,
      query: { limit: PAGE_SIZE, offset },
      throwOnError: true,
    });

    return response.data;
  });

  for (const secret of secrets) {
    await deleteRepositorySecret({
      path: { ...path, secretName: secret.name },
      throwOnError: true,
    });
  }

  onProgress({ phase: 'secrets', deletedCount: secrets.length });

  await deleteRepository({ path, throwOnError: true });
  onProgress({ phase: 'repository', deletedCount: 1 });
}
