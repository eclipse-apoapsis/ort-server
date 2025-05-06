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
import { Fragment } from 'react';

import { useVersionsServiceGetApiV1VersionsSuspense } from '@/api/queries/suspense';
import { Card, CardContent, CardHeader } from '@/components/ui/card';
import { Separator } from '@/components/ui/separator';
import { GITHUB_LATEST_RELEASE_DOWNLOAD_URL } from '@/lib/constants';

export const About = () => {
  const { data: versionData } = useVersionsServiceGetApiV1VersionsSuspense();

  // Check if the ORT Server version is a release candidate (RC) version
  // by looking for the '-RC.' substring in the version string.
  // If it is, use the latest release download URL.
  // Otherwise, use the specific version download URL.
  const ortServerVersion = versionData['ORT Server'];
  const isVersionCandidate = ortServerVersion?.includes('-RC.');
  const baseUrl = isVersionCandidate
    ? GITHUB_LATEST_RELEASE_DOWNLOAD_URL
    : GITHUB_LATEST_RELEASE_DOWNLOAD_URL.replace(
        'latest/download',
        `download/${ortServerVersion}`
      );

  return (
    <Card className='mx-auto w-full max-w-4xl'>
      <CardHeader>
        <h2 className='text-3xl font-bold tracking-tight'>About</h2>
      </CardHeader>
      <Separator />
      <CardContent className='pt-6'>
        <div>
          <h3 className='mb-4 font-semibold'>Version Information</h3>
          <div className='grid grid-cols-[auto_1fr] gap-x-8 gap-y-1'>
            {Object.entries(versionData).map(([key, value]) => (
              <Fragment key={key}>
                <div className='text-muted-foreground font-semibold'>{key}</div>
                <div className='text-muted-foreground'>{value}</div>
              </Fragment>
            ))}
          </div>
          <h3 className='mt-8 mb-4 font-semibold'>
            ORT Server Client (osc) Download
          </h3>
          <div className='grid grid-cols-[auto_1fr] gap-x-8 gap-y-1'>
            <div className='text-muted-foreground font-semibold'>Linux</div>
            <a
              href={`${baseUrl}/osc-cli-linux-x64.tar.gz`}
              className='font-semibold text-blue-400 hover:underline'
              target='_blank'
              rel='noopener noreferrer'
            >
              osc-cli-linux-x64
            </a>
            <div className='text-muted-foreground font-semibold'>
              macOS (ARM64)
            </div>
            <a
              href={`${baseUrl}/osc-cli-macos-arm64.tar.gz`}
              className='font-semibold text-blue-400 hover:underline'
              target='_blank'
              rel='noopener noreferrer'
            >
              osc-cli-macos-arm64
            </a>
            <div className='text-muted-foreground font-semibold'>
              macOS (x64)
            </div>
            <a
              href={`${baseUrl}/osc-cli-macos-x64.tar.gz`}
              className='font-semibold text-blue-400 hover:underline'
              target='_blank'
              rel='noopener noreferrer'
            >
              osc-cli-macos-x64
            </a>
            <div className='text-muted-foreground font-semibold'>Windows</div>
            <a
              href={`${baseUrl}/osc-cli-windows-x64.zip`}
              className='font-semibold text-blue-400 hover:underline'
              target='_blank'
              rel='noopener noreferrer'
            >
              osc-cli-windows-x64
            </a>
          </div>
        </div>
      </CardContent>
    </Card>
  );
};

export const Route = createFileRoute('/about/')({
  component: About,
});
