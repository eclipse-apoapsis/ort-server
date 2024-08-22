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

import { ChevronDownIcon } from '@radix-ui/react-icons';
import { useSuspenseQuery } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';

import { useRepositoriesServiceGetOrtRunByIndexKey } from '@/api/queries';
import { OpenAPI, RepositoriesService } from '@/api/requests';
import { LoadingIndicator } from '@/components/loading-indicator';
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Separator } from '@/components/ui/separator';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { calculateDuration } from '@/helpers/get-run-duration';
import { getStatusBackgroundColor } from '@/helpers/get-status-colors';

const RunComponent = () => {
  const params = Route.useParams();
  const locale = navigator.language;

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
      a.download = filename ?? `${runId}_logs.zip`;
      a.click();
      window.URL.revokeObjectURL(url);
    } catch (error) {
      console.error(error);
    }
  };

  const downloadReports = async ({
    runId,
    fileName,
  }: {
    runId: number;
    fileName: string;
  }) => {
    try {
      const response = await fetch(
        `${OpenAPI.BASE}/api/v1/runs/${runId}/reporter/${fileName}`,
        {
          headers: {
            Authorization: `Bearer ${OpenAPI.TOKEN}`,
          },
        }
      );
      // Convert the response to a Blob
      const blob = await response.blob();
      // Create a temporary URL for the Blob
      const url = window.URL.createObjectURL(blob);
      // Create an anchor element
      const a = document.createElement('a');
      a.href = url;
      a.download = fileName;
      // Programmatically trigger a click event on the anchor element
      a.click();
      // Clean up by revoking the URL
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
      <CardHeader className='flex flex-row items-start'>
        <div className='grid gap-2'>
          <CardTitle>{ortRun.index}</CardTitle>
        </div>
      </CardHeader>
      <CardContent>
        <Badge className={`border ${getStatusBackgroundColor(ortRun.status)}`}>
          {ortRun.status}
        </Badge>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Run details</TableHead>
              <TableHead className='sr-only'>Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow>
              <TableCell>Global run ID</TableCell>
              <TableCell>
                <div className='font-medium'>{ortRun.id}</div>
              </TableCell>
            </TableRow>
            <TableRow>
              <TableCell>Created at</TableCell>
              <TableCell>
                <div className='font-medium'>
                  {new Date(ortRun.createdAt).toLocaleString(locale)}
                </div>
              </TableCell>
            </TableRow>
            {ortRun.finishedAt && (
              <TableRow>
                <TableCell>Finished at</TableCell>
                <TableCell>
                  <div className='font-medium'>
                    {new Date(ortRun.finishedAt).toLocaleString(locale)}
                  </div>
                </TableCell>
              </TableRow>
            )}
            <TableRow>
              <TableCell>Revision</TableCell>
              <TableCell>
                <div className='font-medium'>{ortRun.revision}</div>
              </TableCell>
            </TableRow>
            {ortRun.jobConfigContext && (
              <TableRow>
                <TableCell>Job configuration context</TableCell>
                <TableCell>
                  <div className='font-medium'>{ortRun.jobConfigContext}</div>
                </TableCell>
              </TableRow>
            )}
            {ortRun.path && (
              <TableRow>
                <TableCell>Path</TableCell>
                <TableCell>
                  <div className='font-medium'>{ortRun.path}</div>
                </TableCell>
              </TableRow>
            )}
            {ortRun.jobs.reporter?.reportFilenames && (
              <TableRow>
                <TableCell>Reports</TableCell>
                <TableCell>
                  {ortRun.jobs.reporter?.reportFilenames.map((filename) => (
                    <div key={filename} className='pb-2'>
                      <Button
                        onClick={() =>
                          downloadReports({
                            runId: ortRun.id,
                            fileName: filename,
                          })
                        }
                        variant='outline'
                        className='font-semibold text-blue-400'
                      >
                        {filename}
                      </Button>
                    </div>
                  ))}
                </TableCell>
              </TableRow>
            )}
            <TableRow>
              <TableCell>Logs</TableCell>
              <TableCell>
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
              </TableCell>
            </TableRow>
            {ortRun.issues.length > 0 && (
              <TableRow>
                <TableCell>Issues</TableCell>
                <TableCell>
                  <pre>{JSON.stringify(ortRun.issues, null, 2)}</pre>
                </TableCell>
              </TableRow>
            )}
            {ortRun.jobConfigs.parameters && (
              <>
                <TableRow>
                  <TableCell
                    colSpan={2}
                    className='font-semibold text-blue-400'
                  >
                    Parameters:
                  </TableCell>
                </TableRow>
                {Object.entries(ortRun.jobConfigs.parameters).map(
                  ([key, value]) => (
                    <TableRow key={key}>
                      <TableCell>{key}</TableCell>
                      <TableCell>{value}</TableCell>
                    </TableRow>
                  )
                )}
              </>
            )}
            {ortRun.labels && (
              <>
                <TableRow>
                  <TableCell
                    colSpan={2}
                    className='font-semibold text-blue-400'
                  >
                    Labels:
                  </TableCell>
                </TableRow>
                {Object.entries(ortRun.labels).map(([key, value]) => (
                  <TableRow key={key}>
                    <TableCell>{key}</TableCell>
                    <TableCell>{value}</TableCell>
                  </TableRow>
                ))}
              </>
            )}
          </TableBody>
        </Table>
        <Separator />

        <div className='ml-2 mt-4'>
          <h3>Jobs</h3>
          <div className='text-sm text-gray-500'>
            Jobs that were included in this ORT Run. Click the job name to see
            details.
          </div>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Job</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className='text-right'>Duration</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {Object.entries(ortRun.jobs).map(([jobName, job]) => (
                <TableRow key={jobName}>
                  <TableCell>
                    <Accordion type='single' collapsible>
                      <AccordionItem value={jobName} className='border-none'>
                        <AccordionTrigger className='py-0 font-semibold capitalize text-blue-400 hover:underline'>
                          {jobName}
                        </AccordionTrigger>
                        <AccordionContent>
                          <pre>{JSON.stringify(job, null, 2)}</pre>
                        </AccordionContent>
                      </AccordionItem>
                    </Accordion>
                  </TableCell>
                  <TableCell>
                    <Badge
                      className={`border ${getStatusBackgroundColor(job?.status)}`}
                    >
                      {job?.status}
                    </Badge>
                  </TableCell>
                  <TableCell className='text-right'>
                    {job?.startedAt && job?.finishedAt
                      ? calculateDuration(job?.startedAt, job?.finishedAt)
                      : '-'}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      </CardContent>
    </Card>
  );
};

export const Route = createFileRoute(
  '/_layout/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/'
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
  component: RunComponent,
  pendingComponent: LoadingIndicator,
});
