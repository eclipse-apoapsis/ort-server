/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import { useSuspenseQueries } from '@tanstack/react-query';
import { useMemo } from 'react';

import {
  UseRepositoriesServiceGetOrtRunsByRepositoryIdKeyFn,
  UseVulnerabilitiesServiceGetVulnerabilitiesByRunIdKeyFn,
} from '@/api/queries';
import { useRepositoriesServiceGetRepositoriesByProductIdSuspense } from '@/api/queries/suspense';
import {
  RepositoriesService,
  VulnerabilitiesService,
  VulnerabilityWithIdentifier,
} from '@/api/requests';
import { ALL_ITEMS } from '@/lib/constants';

export type VulnerabilityWithRepositoryCount = VulnerabilityWithIdentifier & {
  repositoryCount: number;
};

export function useVulnerabilitiesByProductIdSuspense({
  productId,
}: {
  productId: number;
}): VulnerabilityWithRepositoryCount[] {
  // Get all repositories of the product.
  const { data: repositories } =
    useRepositoriesServiceGetRepositoriesByProductIdSuspense({
      productId,
    });

  // Get the newest ORT run for each repository for which the advisor job has succeeded.
  const runsForAdvisorQueries = useSuspenseQueries({
    queries: repositories.data.map((repository) => ({
      queryKey: [
        'runsForAdvisorInProduct',
        ...UseRepositoriesServiceGetOrtRunsByRepositoryIdKeyFn({
          repositoryId: repository.id,
          sort: '-index',
        }),
      ],
      queryFn: async () => {
        const runs = await RepositoriesService.getOrtRunsByRepositoryId({
          repositoryId: repository.id,
          sort: '-index',
        });
        for (const run of runs.data) {
          if (
            run?.jobs.advisor &&
            (run.jobs.advisor.status === 'FINISHED' ||
              'FINISHED_WITH_ISSUES') &&
            run.jobs.advisor.finishedAt
          ) {
            return { ...run, repositoryId: repository.id };
          }
        }
        return null;
      },
    })),
  });

  // Get the vulnerabilities found from the newest succeeded ORT runs.
  const vulnerabilitiesQueries = useSuspenseQueries({
    queries: runsForAdvisorQueries.flatMap((lastRunForRepository) => {
      const lastRunData = lastRunForRepository.data;
      return lastRunData
        ? [
            {
              queryKey: [
                'productVulnerabilities',
                ...UseVulnerabilitiesServiceGetVulnerabilitiesByRunIdKeyFn({
                  runId: lastRunData.id,
                  limit: ALL_ITEMS,
                }),
              ],
              queryFn: async () => {
                const vulnerabilities =
                  await VulnerabilitiesService.getVulnerabilitiesByRunId({
                    runId: lastRunData.id,
                    limit: ALL_ITEMS,
                  });

                return vulnerabilities.data.map((vulnerability) => ({
                  ...vulnerability,
                  repositoryId: lastRunData.repositoryId,
                }));
              },
            },
          ]
        : [];
    }),
  });

  const result = useMemo(() => {
    const vulnerabilities = vulnerabilitiesQueries.flatMap(
      (query) => query.data
    );

    // Count occurrences by externalId and repositoryId.
    const vulnerabilityRepoMap: Record<
      string,
      { vulnerability: VulnerabilityWithIdentifier; repoSet: Set<number> }
    > = {};
    vulnerabilities.forEach((vulnerability) => {
      const externalId = vulnerability.vulnerability.externalId;
      if (!vulnerabilityRepoMap[externalId]) {
        vulnerabilityRepoMap[externalId] = {
          vulnerability,
          repoSet: new Set(),
        };
      }
      vulnerabilityRepoMap[externalId].repoSet.add(vulnerability.repositoryId);
    });

    // Convert the counts to an array of objects.
    return Object.entries(vulnerabilityRepoMap).map(
      ([, { vulnerability, repoSet }]) => ({
        ...vulnerability,
        repositoryCount: repoSet.size,
      })
    );
  }, [vulnerabilitiesQueries]);

  return result;
}
