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

import { useRepositoriesServiceGetOrtRunByIndexKey } from '@/api/queries';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import {
  TableHeader,
  TableRow,
  TableHead,
  TableBody,
  TableCell,
  Table,
} from '@/components/ui/table';
import { RepositoriesService, OpenAPI } from '@/api/requests';
import { Link, createFileRoute } from '@tanstack/react-router';
import { useSuspenseQuery } from '@tanstack/react-query';
import { Button } from '@/components/ui/button';
import { getStatusBackgroundColor } from '@/helpers/get-status-colors';
import { Badge } from '@/components/ui/badge';

const RunComponent = () => {
  const params = Route.useParams();

  const { data: ortRun } = useSuspenseQuery({
    queryKey: [
      useRepositoriesServiceGetOrtRunByIndexKey,
      params.orgId,
      params.productId,
      params.repoId,
      params.runId,
    ],
    queryFn: async () =>
      await RepositoriesService.getOrtRunByIndex({
        repositoryId: Number.parseInt(params.repoId),
        ortRunIndex: Number.parseInt(params.runId),
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

  async function handleDownload(filename: string) {
    await downloadZipFile({
      runId: Number.parseInt(params.runId),
      fileName: filename,
    });
  }

  return (
    <Card className='w-full max-w-4xl mx-auto'>
      <CardHeader className='flex flex-row items-start'>
        <div className='grid gap-2'>
          <CardTitle>{ortRun.id}</CardTitle>
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
                <div className='font-medium'>{ortRun.createdAt}</div>
              </TableCell>
            </TableRow>
            {ortRun.finishedAt && (
              <TableRow>
                <TableCell>Finished At</TableCell>
                <TableCell>
                  <div className='font-medium'>
                    {ortRun.finishedAt as unknown as string}
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
            <TableRow>
              <TableCell>Job configs</TableCell>
              <TableCell>
                <pre>{JSON.stringify(ortRun.jobConfigs, null, 2)}</pre>
              </TableCell>
            </TableRow>
            <TableRow>
              <TableCell>Resolved job configs</TableCell>
              <TableCell>
                <pre>{JSON.stringify(ortRun.resolvedJobConfigs, null, 2)}</pre>
              </TableCell>
            </TableRow>
            <TableRow>
              <TableCell>Jobs</TableCell>
              <TableCell>
                <pre>{JSON.stringify(ortRun.jobs, null, 2)}</pre>
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
            {ortRun.jobs.reporter?.reportFilenames && (
              <TableRow>
                <TableCell>Result files</TableCell>
                <TableCell>
                  {(
                    ortRun.jobs.reporter?.reportFilenames as unknown as string[]
                  ).map((filename) => (
                    <div key={filename} className='flex flex-col pb-2'>
                      <Link onClick={() => handleDownload(filename)}>
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
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
};

export const Route = createFileRoute(
  '/_layout/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runId/'
)({
  loader: async ({ context, params }) => {
    await context.queryClient.ensureQueryData({
      queryKey: [
        useRepositoriesServiceGetOrtRunByIndexKey,
        params.orgId,
        params.productId,
        params.repoId,
        params.runId,
      ],
      queryFn: () =>
        RepositoriesService.getOrtRunByIndex({
          repositoryId: Number.parseInt(params.repoId),
          ortRunIndex: Number.parseInt(params.runId),
        }),
    });
  },
  component: RunComponent,
});
