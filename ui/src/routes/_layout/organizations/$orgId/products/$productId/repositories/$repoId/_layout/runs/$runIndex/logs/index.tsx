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

import { useSuspenseQuery } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';
import { ChevronDownIcon } from 'lucide-react';

import { useRepositoriesServiceGetOrtRunByIndexKey } from '@/api/queries';
import { OpenAPI, RepositoriesService } from '@/api/requests';
import { LoadingIndicator } from '@/components/loading-indicator';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';

const ReportComponent = () => {
  const params = Route.useParams();

  const { data: ortRun } = useSuspenseQuery({
    queryKey: [
      useRepositoriesServiceGetOrtRunByIndexKey,
      params.repoId,
      params.runIndex,
    ],
    queryFn: async () =>
      await RepositoriesService.getOrtRunByIndex({
        repositoryId: Number.parseInt(params.repoId),
        ortRunIndex: Number.parseInt(params.runIndex),
      }),
  });

  const downloadLogs = async (
    runId: number,
    level: string = '',
    steps: string = ''
  ) => {
    try {
      const response = await fetch(
        `${OpenAPI.BASE}/api/v1/runs/${runId}/logs?level=${level}&steps=${steps}`,
        {
          headers: {
            Authorization: `Bearer ${OpenAPI.TOKEN}`,
          },
        }
      );
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      const filename = response.headers
        .get('content-disposition')
        ?.split(';')
        ?.find((entry) => entry.includes('filename='))
        ?.replace('filename=', '')
        ?.trim();
      a.download = filename ?? `run-${runId}-${level}-logs.zip`;
      a.click();
      window.URL.revokeObjectURL(url);
    } catch (error) {
      console.error(error);
    }
  };

  const handleLevelChoice = (level: string) => {
    // Get the list of steps that were run to only request logs for those steps. The `config` step is
    // always run implicitly and not part of the jobs, so add that manually.
    const jobNames = Object.keys(ortRun.jobs).concat('config');
    downloadLogs(ortRun.id, level, jobNames.join(','));
  };

  return (
    <Card className='mx-auto w-full max-w-4xl'>
      <CardHeader>
        <CardTitle>Logs from global run ID {ortRun.id}</CardTitle>
        <CardDescription>
          Click the button do download. You can select different log levels for
          downloading from the dropdown. Note that the log levels are additive,
          eg. DEBUG logs include all the log levels, while WARN logs include
          only errors and warnings.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <div className='flex align-middle'>
          <Button
            onClick={() => handleLevelChoice('INFO')}
            variant='outline'
            className={'rounded-r-none font-semibold text-blue-400'}
          >
            Download INFO log archive
          </Button>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button
                variant='outline'
                className={'rounded-l-none border-l-0 px-2 pt-1'}
              >
                <ChevronDownIcon />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent>
              <DropdownMenuItem
                onClick={() => {
                  handleLevelChoice('ERROR');
                }}
              >
                Download ERROR log archive
              </DropdownMenuItem>
              <DropdownMenuItem
                onClick={() => {
                  handleLevelChoice('WARN');
                }}
              >
                Download WARN log archive
              </DropdownMenuItem>
              <DropdownMenuItem
                onClick={() => {
                  handleLevelChoice('INFO');
                }}
              >
                Download INFO log archive
              </DropdownMenuItem>
              <DropdownMenuItem
                onClick={() => {
                  handleLevelChoice('DEBUG');
                }}
              >
                Download DEBUG log archive
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </CardContent>
    </Card>
  );
};

export const Route = createFileRoute(
  '/_layout/organizations/$orgId/products/$productId/repositories/$repoId/_layout/runs/$runIndex/logs/'
)({
  loader: async ({ context, params }) => {
    await context.queryClient.ensureQueryData({
      queryKey: [
        useRepositoriesServiceGetOrtRunByIndexKey,
        params.repoId,
        params.runIndex,
      ],
      queryFn: () =>
        RepositoriesService.getOrtRunByIndex({
          repositoryId: Number.parseInt(params.repoId),
          ortRunIndex: Number.parseInt(params.runIndex),
        }),
    });
  },
  component: ReportComponent,
  pendingComponent: LoadingIndicator,
});
