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
import { ReactNode, useRef } from 'react';
import { FieldPath, UseFormReturn } from 'react-hook-form';

import { Accordion, AccordionContent } from '@/components/ui/accordion';
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from '@/components/ui/collapsible';
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
  ENVIRONMENT_DEFINITION_SCHEMAS,
  FieldEntry,
} from '@/lib/environment-definition-fields';
import { EnvironmentDefinitionEntry } from '@/lib/types';
import { CreateRunFormValues } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/create-run/-components';

type PackageManagerFieldProps = {
  pmKey: string;
  entry: FieldEntry;
  form: UseFormReturn<CreateRunFormValues>;
  infrastructureServices: InfrastructureServiceWithHierarchy[];
};

function PackageManagerField({
  pmKey,
  entry,
  form,
  infrastructureServices,
}: PackageManagerFieldProps): ReactNode {
  const name =
    `jobConfigs.analyzer.environmentDefinitions.${pmKey}.0.${entry.key}` as FieldPath<CreateRunFormValues>;
  const { def } = entry;

  if (def.type === 'service') {
    return (
      <FormField
        control={form.control}
        name={name}
        render={({ field }) => {
          const value =
            typeof field.value === 'string' && field.value.length > 0
              ? field.value
              : undefined;
          return (
            <FormItem>
              <FormLabel>Service</FormLabel>
              <Select value={value} onValueChange={(v) => field.onChange(v)}>
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
                Select the infrastructure service from a chosen hierarchy level.
              </FormDescription>
              <FormMessage />
            </FormItem>
          );
        }}
      />
    );
  }

  if (def.type === 'string') {
    return (
      <FormField
        control={form.control}
        name={name}
        render={({ field }) => (
          <FormItem>
            <FormLabel>{def.label}</FormLabel>
            <FormControl>
              <Input
                {...field}
                value={(field.value as string) ?? ''}
                placeholder={def.optional ? '(optional)' : undefined}
              />
            </FormControl>
            <FormDescription>{def.description}</FormDescription>
            <FormMessage />
          </FormItem>
        )}
      />
    );
  }

  if (def.type === 'boolean') {
    return (
      <FormField
        control={form.control}
        name={name}
        render={({ field }) => (
          <FormItem className='flex flex-row items-center justify-between rounded-lg border p-4'>
            <div className='space-y-0.5'>
              <FormLabel>{def.label}</FormLabel>
              <FormDescription>{def.description}</FormDescription>
            </div>
            <FormControl>
              <Switch
                checked={((field.value as string) ?? 'true') === 'true'}
                onCheckedChange={(checked) =>
                  field.onChange(checked ? 'true' : 'false')
                }
              />
            </FormControl>
          </FormItem>
        )}
      />
    );
  }

  return (
    <FormField
      control={form.control}
      name={name}
      render={({ field }) => {
        const value =
          typeof field.value === 'string' && field.value.length > 0
            ? field.value
            : undefined;
        return (
          <FormItem>
            <FormLabel>{def.label}</FormLabel>
            <Select value={value} onValueChange={(v) => field.onChange(v)}>
              <FormControl>
                <SelectTrigger className='w-full'>
                  <SelectValue placeholder={def.placeholder} />
                </SelectTrigger>
              </FormControl>
              <SelectContent>
                {def.values.map((mode) => (
                  <SelectItem key={mode} value={mode}>
                    {mode.replaceAll('_', ' ')}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <FormDescription>{def.description}</FormDescription>
            <FormMessage />
          </FormItem>
        );
      }}
    />
  );
}

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
    <Collapsible className='flex flex-col gap-2'>
      <CollapsibleTrigger className='flex w-full items-start justify-between gap-4 text-left [&[data-state=open]>svg]:rotate-180'>
        <div>
          <h3>Environment configuration</h3>
          <p className='mt-1 text-sm text-gray-500'>
            Configure the credentials for different package managers to access
            private artifact repositories.
          </p>
        </div>
        <ChevronDownIcon className='text-muted-foreground mt-1 size-4 shrink-0 transition-transform duration-200' />
      </CollapsibleTrigger>
      <CollapsibleContent>
        <Accordion type='multiple' className='ml-4 flex flex-col gap-2'>
          {ENVIRONMENT_DEFINITION_SCHEMAS.map((schema) => (
            <AccordionPrimitive.Item
              key={schema.key}
              value={schema.key}
              className='rounded-lg border'
            >
              <AccordionPrimitive.Header className='flex flex-row items-center'>
                <AccordionPrimitive.Trigger className='focus-visible:border-ring focus-visible:ring-ring/50 flex flex-1 items-center gap-2 px-4 py-4 text-left text-sm font-medium transition-all outline-none hover:underline focus-visible:ring-[3px] [&[data-state=open]>svg]:rotate-180'>
                  {schema.label}
                  <ChevronDownIcon className='text-muted-foreground size-4 shrink-0 translate-y-0.5 transition-transform duration-200' />
                </AccordionPrimitive.Trigger>
                <div className='pr-4'>
                  <Switch
                    checked={enabledDefinitions[schema.key] ?? false}
                    onCheckedChange={(checked) =>
                      onToggle(schema.key, checked, schema.defaultEntries)
                    }
                  />
                </div>
              </AccordionPrimitive.Header>
              <AccordionContent>
                <div className='flex flex-col gap-4 px-4 pb-4'>
                  {schema.fields.map((entry) => (
                    <PackageManagerField
                      key={entry.key}
                      pmKey={schema.key}
                      entry={entry}
                      form={form}
                      infrastructureServices={infrastructureServices}
                    />
                  ))}
                </div>
              </AccordionContent>
            </AccordionPrimitive.Item>
          ))}
        </Accordion>
      </CollapsibleContent>
    </Collapsible>
  );
};
