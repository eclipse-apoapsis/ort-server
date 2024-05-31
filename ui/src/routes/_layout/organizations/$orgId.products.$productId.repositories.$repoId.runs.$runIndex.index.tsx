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
import { createFileRoute, Link } from '@tanstack/react-router';

import { useRepositoriesServiceGetOrtRunByIndexKey } from '@/api/queries';
import { OpenAPI, RepositoriesService } from '@/api/requests';
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
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

  const { data: ortRun } = useSuspenseQuery({
    queryKey: [
      useRepositoriesServiceGetOrtRunByIndexKey,
      params.orgId,
      params.productId,
      params.repoId,
      params.runIndex,
    ],
    queryFn: async () =>
      await RepositoriesService.getOrtRunByIndex({
        repositoryId: Number.parseInt(params.repoId),
        ortRunIndex: Number.parseInt(params.runIndex),
      }),
  });

  const downloadZipFile = async ({
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

  async function handleDownload(runId: number, filename: string) {
    await downloadZipFile({
      runId: runId,
      fileName: filename,
    });
  }

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
              <TableHead>Run parameter</TableHead>
              <TableHead>Value</TableHead>
              <TableHead className='sr-only'>Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow>
              <TableCell>Created At</TableCell>
              <TableCell>
                <div className='font-medium'>
                  {new Date(ortRun.createdAt).toLocaleString()}
                </div>
              </TableCell>
            </TableRow>
            {ortRun.finishedAt && (
              <TableRow>
                <TableCell>Finished At</TableCell>
                <TableCell>
                  <div className='font-medium'>
                    {new Date(ortRun.finishedAt).toLocaleString()}
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
            {ortRun.jobs.reporter?.reportFilenames && (
              <TableRow>
                <TableCell>Reports</TableCell>
                <TableCell>
                  {ortRun.jobs.reporter?.reportFilenames.map((filename) => (
                    <div key={filename} className='flex flex-col pb-2'>
                      <Link onClick={() => handleDownload(ortRun.id, filename)}>
                        <Button
                          variant='outline'
                          className='font-semibold text-blue-400'
                        >
                          {filename}
                        </Button>
                      </Link>
                    </div>
                  ))}
                </TableCell>
              </TableRow>
            )}
            {ortRun.issues.length > 0 && (
              <TableRow>
                <TableCell>Issues</TableCell>
                <TableCell>
                  <pre>{JSON.stringify(ortRun.issues, null, 2)}</pre>
                </TableCell>
              </TableRow>
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
                <TableHead className='text-right'>
                  Duration (hh:mm:ss)
                </TableHead>
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
        params.orgId,
        params.productId,
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
});
