/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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
import CycloneDXDark from '@/assets/cyclonedx-logo-black.svg';
import CycloneDXLight from '@/assets/cyclonedx-logo-white.svg';
import SPDX from '@/assets/spdx-logo-color.svg';
import { LoadingIndicator } from '@/components/loading-indicator';
import { useTheme } from '@/components/theme-provider-context';
import { ToastError } from '@/components/toast-error';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader } from '@/components/ui/card';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { toast } from '@/lib/toast';

const SBOMComponent = () => {
  const params = Route.useParams();
  // For CycloneDX, no SVG logo was found which would adapt to the color
  // theme automatically. Two different logos are provided instead in
  // https://cyclonedx.org/about/branding/ and the correct one is selected
  // based on the current theme.
  const { activeTheme } = useTheme();

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

  const spdxReports = ortRun.jobs.reporter?.reportFilenames?.filter(
    (filename) => filename.toLowerCase().includes('spdx')
  );

  const cycloneDxReports = ortRun.jobs.reporter?.reportFilenames?.filter(
    (filename) => filename.toLowerCase().includes('cyclonedx')
  );

  return (
    <div className='flex flex-col gap-4'>
      <div className='grid grid-cols-2 gap-4'>
        <Card>
          <CardHeader className='text-center'>
            <a
              href='https://cyclonedx.org/'
              target='_blank'
              rel='noopener noreferrer'
            >
              <Tooltip>
                <TooltipTrigger>
                  <img
                    alt='CycloneDX'
                    src={
                      activeTheme === 'dark' ? CycloneDXLight : CycloneDXDark
                    }
                    width={230}
                  />
                </TooltipTrigger>
                <TooltipContent>Click to visit the homepage</TooltipContent>
              </Tooltip>
            </a>
          </CardHeader>
          <div className='flex justify-around gap-2 pb-6'>
            {cycloneDxReports && cycloneDxReports.length > 0
              ? cycloneDxReports.map((filename) => (
                  <div key={filename}>
                    <Button
                      variant='outline'
                      className='h-auto whitespace-normal font-semibold text-blue-400'
                      onClick={() => handleDownload(ortRun.id, filename)}
                    >
                      <div className='break-all'>
                        {filename.includes('json')
                          ? 'JSON Format'
                          : 'XML Format'}
                      </div>
                    </Button>
                  </div>
                ))
              : 'No CycloneDX reports available.'}
          </div>

          <CardContent className='text-sm text-muted-foreground'>
            CycloneDX is a standard format for creating software Bill of
            Materials (SBOMs) to improve software supply chain transparency and
            security.
          </CardContent>
        </Card>
        <Card>
          <CardHeader className='text-center'>
            <a
              href='https://spdx.dev/'
              target='_blank'
              rel='noopener noreferrer'
            >
              <Tooltip>
                <TooltipTrigger>
                  <img alt='SPDX' src={SPDX} width={150} />
                </TooltipTrigger>
                <TooltipContent>Click to visit the homepage</TooltipContent>
              </Tooltip>
            </a>
          </CardHeader>
          <div className='flex justify-around gap-2 pb-6'>
            {spdxReports && spdxReports.length > 0
              ? spdxReports.map((filename) => (
                  <div key={filename}>
                    <Button
                      variant='outline'
                      className='h-auto whitespace-normal font-semibold text-blue-400'
                      onClick={() => handleDownload(ortRun.id, filename)}
                    >
                      <div className='break-all'>
                        {filename.includes('json')
                          ? 'JSON Format'
                          : 'YAML Format'}
                      </div>
                    </Button>
                  </div>
                ))
              : 'No SPDX reports available.'}
          </div>

          <CardContent className='text-sm text-muted-foreground'>
            System Package Data Exchange (SPDX) is an open standard capable of
            representing systems with software components as SBOMs (Software
            Bill of Materials).
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

export const Route = createFileRoute(
  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/sbom/'
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
  component: SBOMComponent,
  pendingComponent: LoadingIndicator,
});
