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

import { useParams, useRouter } from '@tanstack/react-router';
import { Check, ChevronsUpDown } from 'lucide-react';
import { useState } from 'react';

import { SearchableInfiniteList } from '@/components/searchable-infinite-list';
import { Button } from '@/components/ui/button';
import { CommandItem } from '@/components/ui/command';
import { FormControl } from '@/components/ui/form';
import { capitalize } from '@/helpers/capitalize';
import { useInfrastructureServices } from '@/hooks/use-infrastructure-services';
import { cn } from '@/lib/utils';

type InfrastructureServiceSelectProps = {
  /** The name of the selected service, empty when nothing is selected yet. */
  value: string | undefined;
  onChange: (value: string) => void;
  placeholder: string;
  className?: string;
};

/**
 * A field for picking one of the infrastructure services of a hierarchy.
 *
 * The only place this is used is a field of an environment definition, which sits three components
 * deep in the create-run form, so it reads the repository it belongs to and what the user is
 * allowed to see itself rather than having them handed down through all of them.
 *
 * The services are read a page at a time, and only while the list is open. The trigger shows the
 * name that is selected as it is, without looking it up, so that a service saved earlier, or one a
 * rerun refers to from outside the hierarchy, is shown even when its page has not been read.
 *
 * Render it inside a `FormField`, as it brings the `FormControl` of the field with it.
 */
export const InfrastructureServiceSelect = ({
  value,
  onChange,
  placeholder,
  className,
}: InfrastructureServiceSelectProps) => {
  const { orgId, productId, repoId } = useParams({ strict: false });
  const permissions = useRouter().options.context.permissions;

  const [open, setOpen] = useState(false);
  const services = useInfrastructureServices({
    orgId,
    productId,
    repoId,
    permissions,
    enabled: open,
  });

  return (
    <SearchableInfiniteList
      trigger={
        <FormControl>
          <Button
            variant='outline'
            role='combobox'
            aria-expanded={open}
            className={cn(
              'w-full justify-between gap-2 font-normal',
              className
            )}
          >
            <span
              className={cn(
                'min-w-0 truncate text-left',
                !value && 'text-muted-foreground'
              )}
            >
              {value || placeholder}
            </span>
            <ChevronsUpDown className='ml-2 h-4 w-4 shrink-0 opacity-50' />
          </Button>
        </FormControl>
      }
      open={open}
      onOpenChange={setOpen}
      list={services}
      getItemKey={(service) => `${service.hierarchy}:${service.name}`}
      renderItem={(service) => (
        <CommandItem
          value={`${service.hierarchy}:${service.name}`}
          onSelect={() => {
            onChange(service.name);
            setOpen(false);
          }}
        >
          <Check
            className={cn(
              'mr-2 h-4 w-4',
              service.name === value ? 'opacity-100' : 'opacity-0'
            )}
          />
          <span className='truncate'>
            {`${service.name} (${capitalize(service.hierarchy)})`}
          </span>
        </CommandItem>
      )}
      searchable={false}
      emptyMessage='No infrastructure services found.'
      errorMessage='Failed to load infrastructure services'
    />
  );
};
