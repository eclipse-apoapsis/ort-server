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
import { PlusIcon } from 'lucide-react';

import { useOrganizationsServiceGetOrganizationsKey } from '@/api/queries';
import { OrganizationsService } from '@/api/requests';
import { LoadingIndicator } from '@/components/loading-indicator';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui/tooltip';

export const IndexPage = () => {
  const { data } = useSuspenseQuery({
    queryKey: [useOrganizationsServiceGetOrganizationsKey],
    queryFn: () => OrganizationsService.getOrganizations({ limit: 1000 }),
  });

  return (
    <TooltipProvider>
      <Card className='mx-auto w-full max-w-4xl'>
        <CardHeader>
          <CardTitle>Organizations</CardTitle>
          <CardDescription>
            Browse your organizations or create a new one
          </CardDescription>
          <div className='py-2'>
            <Tooltip>
              <TooltipTrigger asChild>
                <Button asChild size='sm' className='ml-auto gap-1'>
                  <Link to='/create-organization'>
                    New organization
                    <PlusIcon className='h-4 w-4' />
                  </Link>
                </Button>
              </TooltipTrigger>
              <TooltipContent>Create a new organization</TooltipContent>
            </Tooltip>
          </div>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className='flex flex-row items-center justify-between pb-1.5 pr-0'>
                  Organizations
                </TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data?.data.map((org) => {
                return (
                  <TableRow key={org.id}>
                    <TableCell>
                      <div>
                        <Link
                          className='font-semibold text-blue-400 hover:underline'
                          to={`/organizations/$orgId`}
                          params={{ orgId: org.id.toString() }}
                        >
                          {org.name}
                        </Link>
                      </div>
                      <div className='hidden text-sm text-muted-foreground md:inline'>
                        {org.description as unknown as string}
                      </div>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </TooltipProvider>
  );
};

export const Route = createFileRoute('/_layout/')({
  loader: async ({ context }) => {
    await context.queryClient.ensureQueryData({
      queryKey: [useOrganizationsServiceGetOrganizationsKey],
      queryFn: () => OrganizationsService.getOrganizations({ limit: 1000 }),
    });
  },
  component: IndexPage,
  pendingComponent: LoadingIndicator,
});
