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

import { useQuery, useSuspenseQuery } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';
import { ChevronsDownUp, ChevronsUpDown } from 'lucide-react';
import { useState } from 'react';

import { getRepositoryRunOptions } from '@/api/@tanstack/react-query.gen';
import { LoadingIndicator } from '@/components/loading-indicator';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { isJobExpandable } from '@/helpers/job-helpers';
import { createRunPluginsOptions } from '@/hooks/use-create-run-plugins';
import { jobSearchParameterSchema } from '@/schemas';
import { jobSections } from './-components/config-utils';
import { JobConfigurations } from './-components/job-configurations';
import { RunSettings } from './-components/run-settings';

const ConfigComponent = () => {
  const params = Route.useParams();
  const { job } = Route.useSearch();
  const [openSections, setOpenSections] = useState<string[]>(job ? [job] : []);

  const { data: ortRun } = useSuspenseQuery({
    ...getRepositoryRunOptions({
      path: {
        repositoryId: Number.parseInt(params.repoId),
        ortRunIndex: Number.parseInt(params.runIndex),
      },
    }),
  });

  const { data: plugins } = useQuery(
    createRunPluginsOptions(
      ortRun.repositoryId,
      ortRun.resolvedJobConfigContext ?? ortRun.jobConfigContext ?? undefined
    )
  );

  const expandableJobs = jobSections
    .map(({ value }) => value)
    .filter((value) => isJobExpandable(ortRun, value));
  const allOpen =
    expandableJobs.length > 0 &&
    expandableJobs.every((value) => openSections.includes(value));

  return (
    <div className='flex flex-col gap-4'>
      <Card>
        <CardHeader>
          <CardTitle>Run Configuration</CardTitle>
        </CardHeader>
        <CardContent>
          <RunSettings run={ortRun} />
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle className='flex items-center justify-between'>
            <span>Job Configurations</span>
            <Button
              variant='ghost'
              size='xs'
              className='-mr-1.5'
              onClick={() => setOpenSections(allOpen ? [] : expandableJobs)}
            >
              {allOpen ? (
                <>
                  Collapse all
                  <ChevronsDownUp className='size-4' />
                </>
              ) : (
                <>
                  Expand all
                  <ChevronsUpDown className='size-4' />
                </>
              )}
            </Button>
          </CardTitle>
        </CardHeader>
        <CardContent>
          <JobConfigurations
            run={ortRun}
            plugins={plugins ?? []}
            openSections={openSections}
            onOpenSectionsChange={setOpenSections}
          />
        </CardContent>
      </Card>
    </div>
  );
};

export const Route = createFileRoute(
  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/config/'
)({
  validateSearch: jobSearchParameterSchema,
  component: ConfigComponent,
  pendingComponent: LoadingIndicator,
});
