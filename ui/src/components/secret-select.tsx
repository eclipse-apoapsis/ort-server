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

import { Check, ChevronsUpDown } from 'lucide-react';
import { useState } from 'react';

import { SearchableInfiniteList } from '@/components/searchable-infinite-list';
import { Button } from '@/components/ui/button';
import { CommandItem } from '@/components/ui/command';
import { FormControl } from '@/components/ui/form';
import { capitalize } from '@/helpers/capitalize';
import { useSecrets, UseSecretsParams } from '@/hooks/use-secrets';
import { cn } from '@/lib/utils';

type SecretSelectProps = {
  /** The name of the selected secret, empty when nothing is selected yet. */
  value: string | undefined;
  onChange: (value: string) => void;
  placeholder: string;
  orgId?: string;
  productId?: string;
  repositoryId?: string;
  permissions: UseSecretsParams['permissions'];
  className?: string;
};

/**
 * A field for picking one of the secrets of a hierarchy. Which levels it offers depends on the ids
 * that are given and on what the user is allowed to read.
 *
 * The secrets are read a page at a time, and only while the list is open. The trigger shows the
 * name that is selected as it is, without looking it up, so that a name saved earlier is shown even
 * when the page it is on has not been read.
 *
 * Render it inside a `FormField`, as it brings the `FormControl` of the field with it.
 */
export const SecretSelect = ({
  value,
  onChange,
  placeholder,
  orgId,
  productId,
  repositoryId,
  permissions,
  className,
}: SecretSelectProps) => {
  const [open, setOpen] = useState(false);
  const secrets = useSecrets({
    orgId,
    productId,
    repositoryId,
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
      list={secrets}
      getItemKey={(secret) => `${secret.hierarchy}:${secret.name}`}
      renderItem={(secret) => (
        <CommandItem
          value={`${secret.hierarchy}:${secret.name}`}
          onSelect={() => {
            onChange(secret.name);
            setOpen(false);
          }}
        >
          <Check
            className={cn(
              'mr-2 h-4 w-4',
              secret.name === value ? 'opacity-100' : 'opacity-0'
            )}
          />
          <span className='truncate'>
            {`${secret.name} (${capitalize(secret.hierarchy)})`}
          </span>
        </CommandItem>
      )}
      searchable={false}
      emptyMessage='No secrets found.'
      errorMessage='Failed to load secrets'
    />
  );
};
