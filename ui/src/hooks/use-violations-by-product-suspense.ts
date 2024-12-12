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

import {
  UseRepositoriesServiceGetOrtRunsByRepositoryIdKeyFn,
  UseRuleViolationsServiceGetRuleViolationsByRunIdKeyFn,
} from '@/api/queries';
import { useRepositoriesServiceGetRepositoriesByProductIdSuspense } from '@/api/queries/suspense';
import {
  RepositoriesService,
  RuleViolation,
  RuleViolationsService,
} from '@/api/requests';
import { ALL_ITEMS } from '@/lib/constants';

export function useViolationsByProductIdSuspense({
  productId,
}: {
  productId: number;
}): RuleViolation[] {
  // Get all repositories of the product.
  const { data: repositories } =
    useRepositoriesServiceGetRepositoriesByProductIdSuspense({
      productId,
    });

  // Get the newest ORT run for each repository for which the evaluator job has succeeded.
  const runsForEvaluatorQueries = useSuspenseQueries({
    queries: repositories.data.map((repository) => ({
      queryKey: [
        'runsForEvaluatorInProduct',
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
            run?.jobs.evaluator &&
            (run.jobs.evaluator.status === 'FINISHED' ||
              'FINISHED_WITH_ISSUES') &&
            run.jobs.evaluator.finishedAt
          ) {
            return run;
          }
        }
        return null;
      },
    })),
  });

  // Get the # rule violations found from the newest succeeded ORT runs.
  const data = useSuspenseQueries({
    queries: runsForEvaluatorQueries.flatMap((lastRunForRepository) => {
      const lastRunData = lastRunForRepository.data;
      return lastRunData
        ? [
            {
              queryKey: UseRuleViolationsServiceGetRuleViolationsByRunIdKeyFn({
                runId: lastRunData.id,
                limit: ALL_ITEMS,
              }),
              queryFn: async () => {
                const violations =
                  await RuleViolationsService.getRuleViolationsByRunId({
                    runId: lastRunData.id,
                    limit: ALL_ITEMS,
                  });

                return violations;
              },
            },
          ]
        : [];
    }),
  });

  return data.flatMap((query) => query.data.data);
}
