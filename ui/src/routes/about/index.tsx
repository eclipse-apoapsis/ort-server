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
import { ExternalLink } from 'lucide-react';
import { Fragment } from 'react';

import { getVersionsOptions } from '@/api/@tanstack/react-query.gen';
import { Card, CardContent, CardHeader } from '@/components/ui/card';
import { Separator } from '@/components/ui/separator';
import { ORT_SERVER_GITHUB_RELEASES_BASE_URL } from '@/lib/constants';

export const About = () => {
  const { data: versionData } = useSuspenseQuery({
    ...getVersionsOptions(),
  });

  // Check if the ORT Server version is a release candidate (RC) version
  // by looking for the '-RC.' substring in the version string.
  // If it is, use the latest release download URL.
  // Otherwise, use the specific version download URL.
  const ortServerVersion = versionData['ORT Server'];
  const isVersionCandidate = ortServerVersion?.includes('-RC.');
  const baseUrl = isVersionCandidate
    ? `${ORT_SERVER_GITHUB_RELEASES_BASE_URL}/latest/download`
    : `${ORT_SERVER_GITHUB_RELEASES_BASE_URL}/download/${ortServerVersion}`;

  const ortServerVersionInfo = {
    label: 'ORT Server',
    version: ortServerVersion,
    releaseUrl: `${ORT_SERVER_GITHUB_RELEASES_BASE_URL}/tag/${ortServerVersion}`,
    homepageUrl: 'https://eclipse-apoapsis.github.io/ort-server/',
  };

  const ortCoreVersion = versionData['ORT Core'];
  const ortCoreVersionInfo = {
    label: 'ORT Core',
    version: ortCoreVersion,
    releaseUrl: `https://github.com/oss-review-toolkit/ort/releases/tag/${ortCoreVersion}`,
    homepageUrl: 'https://oss-review-toolkit.org/',
  };

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
            {[ortServerVersionInfo, ortCoreVersionInfo].map((info) => (
              <Fragment key={info.label}>
                <div className='text-muted-foreground font-semibold'>
                  <div className='flex items-center'>
                    {info.label}
                    <Link
                      to={info.homepageUrl}
                      target='_blank'
                      rel='noopener noreferrer'
                      className='ml-1 inline-block'
                    >
                      <ExternalLink className='h-4 w-4 text-blue-400' />
                    </Link>
                  </div>
                </div>
                <Link
                  to={info.releaseUrl}
                  className='font-semibold text-blue-400 hover:underline'
                  target='_blank'
                  rel='noopener noreferrer'
                >
                  {info.version}
                </Link>
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
