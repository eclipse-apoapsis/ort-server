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

import { Label } from '@radix-ui/react-dropdown-menu';
import { CaretSortIcon, CheckIcon } from '@radix-ui/react-icons';
import { useSuspenseQuery } from '@tanstack/react-query';
import { Link, useNavigate, useRouterState } from '@tanstack/react-router';
import { useState } from 'react';

import { useOrganizationsServiceGetOrganizationsKey } from '@/api/queries';
import { Organization, OrganizationsService } from '@/api/requests';
import { Button } from '@/components/ui/button';
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/components/ui/command';
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover';
import { Separator } from '@/components/ui/separator';
import { cn } from '@/lib/utils';

export const SideBar = ({ className }: { className?: string }) => {
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();

  const { data } = useSuspenseQuery({
    queryKey: [useOrganizationsServiceGetOrganizationsKey],
    queryFn: () => OrganizationsService.getOrganizations(),
  });

  const matches = useRouterState({ select: (state) => state.matches });

  const organizationMatch = matches.find(
    (match) => match.routeId === '/_layout/organizations/$orgId'
  );

  const adminMatch = matches.find(
    (match) => match.routeId === '/_layout/admin/'
  );

  return (
    <div className={className}>
      <Label className='mb-2 text-xs text-muted-foreground'>Admin</Label>
      <Button
        variant={adminMatch ? 'secondary' : 'ghost'}
        className='w-full justify-start'
        asChild
      >
        <Link to='/admin'>Admin dashboard</Link>
      </Button>
      <Separator className='my-2' />
      <Label className='mb-2 text-xs text-muted-foreground'>
        Organizations
      </Label>
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <Button
            variant='outline'
            role='combobox'
            aria-expanded={open}
            aria-label='Select an organization or page'
            className='w-[200px] justify-between'
          >
            {organizationMatch ? (
              <span>{organizationMatch.context.breadcrumbs.organization}</span>
            ) : (
              <span>View organization...</span>
            )}
            <CaretSortIcon className='ml-auto h-4 w-4 shrink-0 opacity-50' />
          </Button>
        </PopoverTrigger>
        <PopoverContent className='w-[200px] p-0'>
          <Command>
            <CommandInput placeholder='Search organization...' />
            <CommandEmpty>No organization found.</CommandEmpty>
            <CommandList>
              <CommandGroup>
                {data?.data.map((organization: Organization) => (
                  <CommandItem
                    key={organization.id}
                    onSelect={() => {
                      navigate({ to: `/organizations/${organization.id}` });
                      setOpen(false);
                    }}
                    className='text-sm'
                  >
                    {organization.name}
                    <CheckIcon
                      className={cn(
                        'ml-auto h-4 w-4',
                        organizationMatch &&
                          organizationMatch.id ===
                            `/_layout/organizations/${organization.id}`
                          ? 'opacity-100'
                          : 'opacity-0'
                      )}
                    />
                  </CommandItem>
                ))}
              </CommandGroup>
            </CommandList>
          </Command>
        </PopoverContent>
      </Popover>
    </div>
  );
};
