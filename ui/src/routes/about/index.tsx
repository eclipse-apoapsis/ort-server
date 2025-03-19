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

export const About = () => {
  const { data: versionData } = useVersionsServiceGetApiV1VersionsSuspense();

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
        </div>
      </CardContent>
    </Card>
  );
};

export const Route = createFileRoute('/about/')({
  component: About,
});
