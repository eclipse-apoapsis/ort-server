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

import * as AccordionPrimitive from '@radix-ui/react-accordion';
import { ChevronDownIcon } from 'lucide-react';
import { useRef } from 'react';
import { UseFormReturn } from 'react-hook-form';

import { Accordion, AccordionContent } from '@/components/ui/accordion';
import {
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Switch } from '@/components/ui/switch';
import { capitalize } from '@/helpers/capitalize';
import { InfrastructureServiceWithHierarchy } from '@/hooks/use-infrastructure-services';
import {
  EnvironmentDefinitionEntry,
  NpmAuthMode,
  npmAuthModes,
  npmEnvironmentDefinitions,
} from '@/lib/types';
import { CreateRunFormValues } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/create-run/-components';

type EnvironmentDefinitionsFieldsProps = {
  form: UseFormReturn<CreateRunFormValues>;
  infrastructureServices: InfrastructureServiceWithHierarchy[];
};

function cloneEntries(
  entries: EnvironmentDefinitionEntry[] | undefined
): EnvironmentDefinitionEntry[] | undefined {
  return entries?.map((entry) => ({ ...entry }));
}

export const EnvironmentDefinitionsFields = ({
  form,
  infrastructureServices,
}: EnvironmentDefinitionsFieldsProps) => {
  const definitionBackups = useRef<
    Record<string, EnvironmentDefinitionEntry[]>
  >({});

  const enabledDefinitions =
    form.watch('jobConfigs.analyzer.environmentDefinitionsEnabled') ?? {};

  function setEnabled(packageManager: string, value: boolean) {
    form.setValue(
      'jobConfigs.analyzer.environmentDefinitionsEnabled',
      { ...enabledDefinitions, [packageManager]: value },
      { shouldDirty: true }
    );
  }

  function setEntries(
    packageManager: string,
    entries: EnvironmentDefinitionEntry[] | undefined
  ) {
    const current =
      form.getValues('jobConfigs.analyzer.environmentDefinitions') ?? {};
    const updated = entries
      ? { ...current, [packageManager]: entries }
      : Object.fromEntries(
          Object.entries(current).filter(([k]) => k !== packageManager)
        );
    form.setValue(
      'jobConfigs.analyzer.environmentDefinitions',
      Object.keys(updated).length > 0 ? updated : undefined,
      { shouldDirty: true }
    );
  }

  function onToggle(
    packageManager: string,
    checked: boolean,
    defaultEntries: EnvironmentDefinitionEntry[]
  ) {
    setEnabled(packageManager, checked);
    if (checked) {
      setEntries(
        packageManager,
        cloneEntries(definitionBackups.current[packageManager]) ??
          cloneEntries(defaultEntries)
      );
    } else {
      definitionBackups.current[packageManager] =
        cloneEntries(
          form.getValues('jobConfigs.analyzer.environmentDefinitions')?.[
            packageManager
          ]
        ) ?? [];
      setEntries(packageManager, undefined);
    }
  }

  return (
    <div className='flex flex-col gap-2'>
      <h3>Environment configuration</h3>
      <div className='mb-2 text-sm text-gray-500'>
        Configure the credentials for different package managers to access
        private artifact repositories.
      </div>
      <Accordion type='multiple' className='flex flex-col gap-2'>
        {/* NPM */}
        <AccordionPrimitive.Item value='npm' className='rounded-lg border'>
          <AccordionPrimitive.Header className='flex flex-row items-center'>
            <AccordionPrimitive.Trigger className='focus-visible:border-ring focus-visible:ring-ring/50 flex flex-1 items-center gap-2 px-4 py-4 text-left text-sm font-medium transition-all outline-none hover:underline focus-visible:ring-[3px] [&[data-state=open]>svg]:rotate-180'>
              Enable NPM environment definition
              <ChevronDownIcon className='text-muted-foreground size-4 shrink-0 translate-y-0.5 transition-transform duration-200' />
            </AccordionPrimitive.Trigger>
            <div className='pr-4'>
              <Switch
                checked={enabledDefinitions['npm'] ?? false}
                onCheckedChange={(checked) =>
                  onToggle('npm', checked, npmEnvironmentDefinitions.npm ?? [])
                }
              />
            </div>
          </AccordionPrimitive.Header>
          <AccordionContent>
            <div className='flex flex-col gap-4 px-4 pb-4'>
              <FormField
                control={form.control}
                name='jobConfigs.analyzer.environmentDefinitions.npm.0.service'
                render={({ field }) => {
                  const selectedValue =
                    typeof field.value === 'string' && field.value.length > 0
                      ? field.value
                      : undefined;
                  return (
                    <FormItem>
                      <FormLabel>Service</FormLabel>
                      <Select
                        value={selectedValue}
                        onValueChange={(value) => field.onChange(value)}
                      >
                        <FormControl>
                          <SelectTrigger className='w-full'>
                            <SelectValue placeholder='Select an infrastructure service' />
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          {infrastructureServices.map((service) => (
                            <SelectItem
                              key={`${service.hierarchy}:${service.name}`}
                              value={service.name}
                            >
                              {`${service.name} (${capitalize(service.hierarchy)})`}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      <FormDescription>
                        Select the infrastructure service from a chosen
                        hierarchy level.
                      </FormDescription>
                      <FormMessage />
                    </FormItem>
                  );
                }}
              />
              <FormField
                control={form.control}
                name='jobConfigs.analyzer.environmentDefinitions.npm.0.scope'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Scope</FormLabel>
                    <FormControl>
                      <Input {...field} placeholder='(optional)' />
                    </FormControl>
                    <FormDescription>
                      Optional NPM scope that this configuration applies to.
                    </FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name='jobConfigs.analyzer.environmentDefinitions.npm.0.email'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Email</FormLabel>
                    <FormControl>
                      <Input {...field} placeholder='(optional)' />
                    </FormControl>
                    <FormDescription>
                      Optional email address used by the NPM registry.
                    </FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name='jobConfigs.analyzer.environmentDefinitions.npm.0.authMode'
                render={({ field }) => {
                  const selectedValue =
                    typeof field.value === 'string' && field.value.length > 0
                      ? (field.value as NpmAuthMode)
                      : undefined;
                  return (
                    <FormItem>
                      <FormLabel>Authorization mode</FormLabel>
                      <Select
                        value={selectedValue}
                        onValueChange={(value) => field.onChange(value)}
                      >
                        <FormControl>
                          <SelectTrigger className='w-full'>
                            <SelectValue placeholder='Select the authorization mode' />
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          {npmAuthModes.map((mode) => (
                            <SelectItem key={mode} value={mode}>
                              {mode.replaceAll('_', ' ')}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      <FormDescription>
                        Pick how the NPM registry authenticates requests.
                      </FormDescription>
                      <FormMessage />
                    </FormItem>
                  );
                }}
              />
            </div>
          </AccordionContent>
        </AccordionPrimitive.Item>
      </Accordion>
    </div>
  );
};
