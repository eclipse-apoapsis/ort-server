/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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
import { ExternalLink } from 'lucide-react';
import { Fragment } from 'react';

import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Separator } from '@/components/ui/separator';
import { config } from '@/config';
import { useUser } from '@/hooks/use-user';

const profileFields = [
  {
    label: 'Username',
    key: 'preferred_username',
  },
  {
    label: 'Email',
    key: 'email',
  },
  {
    label: 'First name',
    key: 'given_name',
  },
  {
    label: 'Last name',
    key: 'family_name',
  },
];

const ProfilePage = () => {
  const user = useUser();
  const profile = user.user?.profile;

  return (
    <div className='mx-auto flex w-full max-w-4xl flex-col gap-6'>
      <Card>
        <CardHeader>
          <CardTitle className='text-3xl font-bold tracking-tight'>
            Profile
          </CardTitle>
          <CardDescription>Your account details.</CardDescription>
        </CardHeader>
        <Separator />
        <CardContent className='pt-6'>
          <div className='grid grid-cols-1 gap-x-8 gap-y-4 sm:grid-cols-[auto_1fr]'>
            {profileFields.map(({ label, key }) => {
              const value = profile?.[key];

              return (
                <Fragment key={key}>
                  <div className='text-muted-foreground font-semibold'>
                    {label}
                  </div>
                  <div>
                    {value === null || value === undefined || value === ''
                      ? '-'
                      : String(value)}
                  </div>
                </Fragment>
              );
            })}
          </div>
        </CardContent>
        <Separator />
        <CardFooter className='flex flex-col items-stretch gap-3 sm:flex-row sm:justify-end'>
          <Button asChild variant='outline'>
            <a
              href={config.accountProfileUrl}
              target='_blank'
              rel='noopener noreferrer'
            >
              Edit profile
              <ExternalLink aria-hidden='true' />
              <span className='sr-only'>(opens in a new tab)</span>
            </a>
          </Button>
          <Button asChild variant='outline'>
            <a
              href={config.accountPasswordUrl}
              target='_blank'
              rel='noopener noreferrer'
            >
              Manage password
              <ExternalLink aria-hidden='true' />
              <span className='sr-only'>(opens in a new tab)</span>
            </a>
          </Button>
        </CardFooter>
      </Card>
    </div>
  );
};

export const Route = createFileRoute('/profile/')({
  component: ProfilePage,
});
