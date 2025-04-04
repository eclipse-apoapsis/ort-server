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
import { ExternalLink } from 'lucide-react';

import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';

const Authorization = () => {
  return (
    <Card>
      <CardHeader className='flex flex-row items-center justify-between space-y-0 pb-2'>
        <CardTitle className='text-sm'>Authorization</CardTitle>
      </CardHeader>
      <CardContent>
        <div>
          <p>
            When an organization, a product, or a repository is created, groups
            for admins, writers and readers for that entity are automatically
            added.
          </p>
          <p className='mt-2'>
            To give user access to an entity, browse to the corresponding entity
            and add the user to the appropriate group in the "Users" section.
          </p>
        </div>
      </CardContent>
      <CardFooter>
        <div className='text-sm'>
          More information on{' '}
          <a
            href={
              'https://eclipse-apoapsis.github.io/ort-server/docs/architecture/authorization'
            }
            target='_blank'
            className='gap-1 text-blue-400 hover:underline'
          >
            <span>how authorization is implemented on ORT Server</span>
            <ExternalLink className='mb-1 ml-1 inline' size={16} />
          </a>
        </div>
      </CardFooter>
    </Card>
  );
};

export const Route = createFileRoute('/admin/users/authorization/')({
  component: Authorization,
});
