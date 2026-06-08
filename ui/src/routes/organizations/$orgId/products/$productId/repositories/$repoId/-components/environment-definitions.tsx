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

import { PlusIcon, TrashIcon } from 'lucide-react';
import { ReactNode } from 'react';
import { FieldPath, UseFormReturn } from 'react-hook-form';

import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
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
  EnvironmentDefinitionSchema,
  FieldEntry,
} from '@/lib/environment-definition-fields';
import {
  EnvironmentDefinitionEntry,
  EnvironmentDefinitions,
} from '@/lib/types';
import { CreateRunFormValues } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/create-run/-components';

type PackageManagerFieldProps = {
  pmKey: string;
  index: number;
  entry: FieldEntry;
  form: UseFormReturn<CreateRunFormValues>;
  infrastructureServices: InfrastructureServiceWithHierarchy[];
};

function PackageManagerField({
  pmKey,
  index,
  entry,
  form,
  infrastructureServices,
}: PackageManagerFieldProps): ReactNode {
  const name =
    `jobConfigs.analyzer.environmentDefinitions.${pmKey}.${index}.${entry.key}` as FieldPath<CreateRunFormValues>;
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
              <Select
                value={value}
                onValueChange={(v) => {
                  form.setValue(name, v, {
                    shouldDirty: true,
                    shouldTouch: true,
                    shouldValidate: true,
                  });
                }}
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
                  form.setValue(name, checked ? 'true' : 'false', {
                    shouldDirty: true,
                    shouldTouch: true,
                    shouldValidate: true,
                  })
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
            <Select
              value={value}
              onValueChange={(v) => {
                form.setValue(name, v, {
                  shouldDirty: true,
                  shouldTouch: true,
                  shouldValidate: true,
                });
              }}
            >
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

type EnvironmentDefinitionCard = {
  schema: EnvironmentDefinitionSchema;
  index: number;
};

const firstSchema = ENVIRONMENT_DEFINITION_SCHEMAS[0];

function cloneDefaultEntry(schema: EnvironmentDefinitionSchema) {
  const defaultEntry = schema.defaultEntries[0];
  return defaultEntry ? { ...defaultEntry } : undefined;
}

function definitionsAsRecord(
  definitions: EnvironmentDefinitions | undefined
): Record<string, EnvironmentDefinitionEntry[]> {
  return (definitions ?? {}) as Record<string, EnvironmentDefinitionEntry[]>;
}

export const EnvironmentDefinitionsFields = ({
  form,
  infrastructureServices,
}: EnvironmentDefinitionsFieldsProps) => {
  const environmentDefinitions = definitionsAsRecord(
    form.watch('jobConfigs.analyzer.environmentDefinitions')
  );

  const cards = ENVIRONMENT_DEFINITION_SCHEMAS.flatMap((schema) =>
    (environmentDefinitions[schema.key] ?? []).map((_, index) => ({
      schema,
      index,
    }))
  );

  function setEnvironmentDefinitions(
    definitions: Record<string, EnvironmentDefinitionEntry[]>
  ) {
    const nonEmptyDefinitions = Object.fromEntries(
      Object.entries(definitions).filter(([, entries]) => entries.length > 0)
    );

    form.setValue(
      'jobConfigs.analyzer.environmentDefinitions',
      Object.keys(nonEmptyDefinitions).length > 0
        ? (nonEmptyDefinitions as EnvironmentDefinitions)
        : undefined,
      { shouldDirty: true, shouldValidate: true }
    );
  }

  function addEnvironmentDefinition() {
    if (!firstSchema) return;

    const defaultEntry = cloneDefaultEntry(firstSchema);
    if (!defaultEntry) return;

    const current = definitionsAsRecord(
      form.getValues('jobConfigs.analyzer.environmentDefinitions')
    );
    setEnvironmentDefinitions({
      ...current,
      [firstSchema.key]: [...(current[firstSchema.key] ?? []), defaultEntry],
    });
  }

  function removeEnvironmentDefinition(schemaKey: string, index: number) {
    const current = definitionsAsRecord(
      form.getValues('jobConfigs.analyzer.environmentDefinitions')
    );
    const entries = [...(current[schemaKey] ?? [])];
    entries.splice(index, 1);

    setEnvironmentDefinitions({
      ...current,
      [schemaKey]: entries,
    });
  }

  function changeEnvironmentDefinitionSchema(
    card: EnvironmentDefinitionCard,
    schemaKey: string
  ) {
    if (schemaKey === card.schema.key) return;

    const schema = ENVIRONMENT_DEFINITION_SCHEMAS.find(
      (schema) => schema.key === schemaKey
    );
    if (!schema) return;

    const defaultEntry = cloneDefaultEntry(schema);
    if (!defaultEntry) return;

    const current = definitionsAsRecord(
      form.getValues('jobConfigs.analyzer.environmentDefinitions')
    );
    const sourceEntries = [...(current[card.schema.key] ?? [])];
    sourceEntries.splice(card.index, 1);

    setEnvironmentDefinitions({
      ...current,
      [card.schema.key]: sourceEntries,
      [schema.key]: [...(current[schema.key] ?? []), defaultEntry],
    });
  }

  return (
    <div className='flex flex-col gap-2'>
      <div>
        <h3>Environment configuration</h3>
        <p className='mt-1 text-sm text-gray-500'>
          Configure the credentials for different package managers to access
          private artifact repositories.
        </p>
      </div>
      <div className='mt-2 flex flex-col gap-4'>
        {cards.map((card) => (
          <Card key={`${card.schema.key}-${card.index}`}>
            <CardHeader className='flex flex-row items-center justify-between gap-4'>
              <CardTitle>
                {card.schema.label} #{card.index + 1}
              </CardTitle>
              <Button
                type='button'
                variant='outline'
                size='sm'
                onClick={() => {
                  removeEnvironmentDefinition(card.schema.key, card.index);
                }}
              >
                <TrashIcon className='h-4 w-4' />
              </Button>
            </CardHeader>
            <CardContent className='flex flex-col gap-4'>
              <FormItem>
                <FormLabel>Package manager</FormLabel>
                <Select
                  value={card.schema.key}
                  onValueChange={(value) => {
                    changeEnvironmentDefinitionSchema(card, value);
                  }}
                >
                  <FormControl>
                    <SelectTrigger className='w-full'>
                      <SelectValue placeholder='Select a package manager' />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>
                    {ENVIRONMENT_DEFINITION_SCHEMAS.map((schema) => (
                      <SelectItem key={schema.key} value={schema.key}>
                        {schema.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </FormItem>
              {card.schema.fields.map((entry) => (
                <PackageManagerField
                  key={entry.key}
                  pmKey={card.schema.key}
                  index={card.index}
                  entry={entry}
                  form={form}
                  infrastructureServices={infrastructureServices}
                />
              ))}
            </CardContent>
          </Card>
        ))}
        <Button
          size='sm'
          className='w-min'
          variant='outline'
          type='button'
          onClick={addEnvironmentDefinition}
        >
          Add environment configuration
          <PlusIcon className='ml-1 h-4 w-4' />
        </Button>
      </div>
    </div>
  );
};
