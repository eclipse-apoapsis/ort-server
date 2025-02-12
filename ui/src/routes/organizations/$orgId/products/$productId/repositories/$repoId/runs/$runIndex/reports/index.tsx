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

import { createFileRoute } from '@tanstack/react-router';

import { prefetchUseRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRunsByOrtRunIndex } from '@/api/queries/prefetch';
import { useRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRunsByOrtRunIndexSuspense } from '@/api/queries/suspense';
import { OpenAPI } from '@/api/requests';
import { LoadingIndicator } from '@/components/loading-indicator';
import { ToastError } from '@/components/toast-error';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { toast } from '@/lib/toast';

const ReportComponent = () => {
  const params = Route.useParams();

  const { data: ortRun } =
    useRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRunsByOrtRunIndexSuspense(
      {
        repositoryId: Number.parseInt(params.repoId),
        ortRunIndex: Number.parseInt(params.runIndex),
      }
    );

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

      // Check if the response status is not in the 2xx range
      if (!response.ok) {
        throw new Error();
      }

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
      toast.error('Unable to download report', {
        description: <ToastError error={error} />,
        duration: Infinity,
        cancel: {
          label: 'Dismiss',
          onClick: () => {},
        },
      });
    }
  };

  async function handleDownload(runId: number, filename: string) {
    await downloadZipFile({
      runId: runId,
      fileName: filename,
    });
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Reports from run {ortRun.index}</CardTitle>
        <CardDescription>Click the file to download.</CardDescription>
      </CardHeader>
      <CardContent>
        {ortRun.jobs.reporter?.reportFilenames &&
        ortRun.jobs.reporter?.reportFilenames.length > 0
          ? ortRun.jobs.reporter.reportFilenames
              .filter(
                (filename) =>
                  !filename.toLowerCase().includes('spdx') &&
                  !filename.toLowerCase().includes('cyclonedx')
              )
              .map((filename) => (
                <div key={filename} className='flex flex-col pb-2'>
                  <Button
                    variant='outline'
                    className='h-auto font-semibold whitespace-normal text-blue-400'
                    onClick={() => handleDownload(ortRun.id, filename)}
                  >
                    <div className='break-all'>{filename}</div>
                  </Button>
                </div>
              ))
          : 'No reports available.'}
      </CardContent>
    </Card>
  );
};

export const Route = createFileRoute(
  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/reports/'
)({
  loader: async ({ context, params }) => {
    await prefetchUseRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRunsByOrtRunIndex(
      context.queryClient,
      {
        repositoryId: Number.parseInt(params.repoId),
        ortRunIndex: Number.parseInt(params.runIndex),
      }
    );
  },
  component: ReportComponent,
  pendingComponent: LoadingIndicator,
});
