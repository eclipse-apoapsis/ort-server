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

import { useParams } from '@tanstack/react-router';
import { PlusIcon, TrashIcon } from 'lucide-react';
import { useEffect, useRef } from 'react';
import { useFieldArray, UseFormReturn } from 'react-hook-form';

import { InfrastructureService } from '@/api';
import { InlineCode } from '@/components/typography.tsx';
import {
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardTitle } from '@/components/ui/card';
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
import { useInfrastructureServices } from '@/hooks/use-infrastructure-services.ts';
import { useUser } from '@/hooks/use-user.ts';
import {
  EnvironmentDefinitions,
  NpmAuthMode,
  npmAuthModes,
  npmEnvironmentDefinitions,
} from '@/lib/types';
import { CreateRunFormValues } from '../_repo-layout/create-run/-create-run-utils';
import { PackageManagerField } from './package-manager-field';

type AnalyzerFieldsProps = {
  form: UseFormReturn<CreateRunFormValues>;
  value: string;
  onToggle: () => void;
  isSuperuser: boolean;
};

export const AnalyzerFields = ({
  form,
  value,
  onToggle,
  isSuperuser,
}: AnalyzerFieldsProps) => {
  const { orgId, productId, repoId } = useParams({ strict: false });
  const {
    fields: environmentVariablesFields,
    append: environmentVariablesAppend,
    remove: environmentVariablesRemove,
  } = useFieldArray({
    name: 'jobConfigs.analyzer.environmentVariables',
    control: form.control,
  });

  const user = useUser();

  const infrastructureServices = useInfrastructureServices({
    orgId,
    productId,
    repoId,
    user,
  });

  // Keep the form in sync with the latest infrastructure services fetched for all hierarchy levels.
  useEffect(() => {
    const sanitized = infrastructureServices.map((serviceWithHierarchy) => {
      const { hierarchy, ...service } = serviceWithHierarchy;
      void hierarchy; // Explicitly ignore the hierarchy helper field.
      return service;
    }) as InfrastructureService[];

    form.setValue('jobConfigs.analyzer.infrastructureServices', sanitized, {
      shouldDirty: false,
    });
  }, [form, infrastructureServices]);

  const cloneEnvironmentDefinitions = (
    definitions: EnvironmentDefinitions | undefined
  ): EnvironmentDefinitions | undefined => {
    if (!definitions) {
      return undefined;
    }
    return Object.fromEntries(
      Object.entries(definitions).map(([key, entries]) => [
        key,
        entries.map((entry) => ({ ...entry })),
      ])
    ) as EnvironmentDefinitions;
  };

  const environmentDefinitionsEnabled =
    form.watch('jobConfigs.analyzer.environmentDefinitionsEnabled') ?? false;

  const environmentDefinitionsBackup = useRef<
    EnvironmentDefinitions | undefined
  >(
    cloneEnvironmentDefinitions(
      form.getValues('jobConfigs.analyzer.environmentDefinitions')
    )
  );

  return (
    <div className='flex flex-row align-middle'>
      <FormField
        control={form.control}
        name='jobConfigs.analyzer.enabled'
        render={({ field }) => (
          <FormControl>
            <Switch
              className='my-4 mr-4 data-[state=checked]:bg-green-500'
              checked={field.value}
              disabled
              onCheckedChange={field.onChange}
            />
          </FormControl>
        )}
      />
      <AccordionItem value={value} className='flex-1'>
        <AccordionTrigger onClick={onToggle}>Analyzer</AccordionTrigger>
        <AccordionContent className='flex flex-col gap-6'>
          <FormField
            control={form.control}
            name='jobConfigs.analyzer.repositoryConfigPath'
            render={({ field }) => (
              <FormItem>
                <FormLabel>Repository configuration path</FormLabel>
                <FormControl>
                  <Input {...field} placeholder='(optional)' />
                </FormControl>
                <FormDescription>
                  The optional path to a repository configuration file. If this
                  is not defined, the repository configuration is read from the
                  root of the analyzed project repository, ie. "./.ort.yml".
                </FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name='jobConfigs.analyzer.allowDynamicVersions'
            render={({ field }) => (
              <FormItem className='flex flex-row items-center justify-between rounded-lg border p-4'>
                <div className='space-y-0.5'>
                  <FormLabel>Allow dynamic versions</FormLabel>
                  <FormDescription>
                    Enable the analysis of projects that use version ranges to
                    declare their dependencies.
                  </FormDescription>
                </div>
                <FormControl>
                  <Switch
                    checked={field.value}
                    onCheckedChange={field.onChange}
                  />
                </FormControl>
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name='jobConfigs.analyzer.skipExcluded'
            render={({ field }) => (
              <FormItem className='flex flex-row items-center justify-between rounded-lg border p-4'>
                <div className='space-y-0.5'>
                  <FormLabel>Skip excluded</FormLabel>
                  <FormDescription>
                    A flag to control whether excluded scopes and paths should
                    be skipped by the analyzer.
                  </FormDescription>
                </div>
                <FormControl>
                  <Switch
                    checked={field.value}
                    onCheckedChange={field.onChange}
                  />
                </FormControl>
              </FormItem>
            )}
          />
          <div className='flex flex-col gap-2'>
            <h3>Environment variables</h3>
            <div className='mb-2 text-sm text-gray-500'>
              A map of key-value pairs to set as environment variables during
              analysis. Use this to specify environment variables that are
              required by the build process. In case of Gradle, this can also be
              used to{' '}
              <a
                className='text-blue-400 hover:underline'
                href={
                  'https://docs.gradle.org/current/userguide/build_environment.html#setting_a_project_property'
                }
                target='_blank'
              >
                set Gradle properties by prefixing them with{' '}
                <InlineCode>ORG_GRADLE_PROJECT_</InlineCode>
              </a>
              .
            </div>
            {environmentVariablesFields.map((field, index) => (
              <div key={field.id} className='flex flex-row items-end space-x-2'>
                <div className='flex-auto'>
                  {index === 0 && <FormLabel className='mb-2'>Name</FormLabel>}
                  <FormField
                    control={form.control}
                    name={`jobConfigs.analyzer.environmentVariables.${index}.name`}
                    render={({ field }) => (
                      <FormItem>
                        <FormControl>
                          <Input {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>
                <div className='flex-auto'>
                  {index === 0 && <FormLabel className='mb-2'>Value</FormLabel>}
                  <FormField
                    control={form.control}
                    name={`jobConfigs.analyzer.environmentVariables.${index}.value`}
                    render={({ field }) => (
                      <FormItem>
                        <FormControl>
                          <Input {...field} value={field.value ?? undefined} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>
                <Button
                  type='button'
                  variant='outline'
                  size='sm'
                  onClick={() => {
                    environmentVariablesRemove(index);
                  }}
                >
                  <TrashIcon className='h-4 w-4' />
                </Button>
              </div>
            ))}
            <Button
              size='sm'
              className='mt-2 w-min'
              variant='outline'
              type='button'
              onClick={() => {
                environmentVariablesAppend({ name: '', value: '' });
              }}
            >
              Add environment variable
              <PlusIcon className='ml-1 h-4 w-4' />
            </Button>
          </div>
          <div className='flex flex-col gap-2'>
            <h3>Environment configuration</h3>
            <FormField
              control={form.control}
              name='jobConfigs.analyzer.environmentDefinitionsEnabled'
              render={({ field }) => (
                <FormItem className='flex flex-row items-center justify-between rounded-lg border p-4'>
                  <div className='space-y-0.5'>
                    <FormLabel>Enable NPM environment definition</FormLabel>
                  </div>
                  <FormControl>
                    <Switch
                      checked={field.value}
                      onCheckedChange={(checked) => {
                        field.onChange(checked);
                        const currentDefinitions = form.getValues(
                          'jobConfigs.analyzer.environmentDefinitions'
                        );

                        if (checked) {
                          if (
                            !currentDefinitions ||
                            Object.keys(currentDefinitions).length === 0
                          ) {
                            const definitionsToRestore =
                              environmentDefinitionsBackup.current ??
                              npmEnvironmentDefinitions;

                            form.setValue(
                              'jobConfigs.analyzer.environmentDefinitions',
                              cloneEnvironmentDefinitions(definitionsToRestore),
                              { shouldDirty: true }
                            );
                          }
                        } else {
                          environmentDefinitionsBackup.current =
                            cloneEnvironmentDefinitions(currentDefinitions);
                          form.setValue(
                            'jobConfigs.analyzer.environmentDefinitions',
                            undefined,
                            { shouldDirty: true }
                          );
                        }
                      }}
                    />
                  </FormControl>
                </FormItem>
              )}
            />
            {environmentDefinitionsEnabled && (
              <Card className='p-4'>
                <CardTitle>NPM</CardTitle>
                <CardContent className='flex flex-col gap-4'>
                  <FormField
                    control={form.control}
                    name='jobConfigs.analyzer.environmentDefinitions.npm.0.service'
                    render={({ field }) => {
                      const selectedValue =
                        typeof field.value === 'string' &&
                        field.value.length > 0
                          ? field.value
                          : undefined;
                      return (
                        <FormItem>
                          <FormLabel>Service</FormLabel>
                          <Select
                            value={selectedValue}
                            onValueChange={(value) => {
                              field.onChange(value);
                            }}
                          >
                            <FormControl>
                              <SelectTrigger className='w-full'>
                                <SelectValue placeholder='Select an infrastructure service' />
                              </SelectTrigger>
                            </FormControl>
                            <SelectContent>
                              {infrastructureServices.map((service) => {
                                const hierarchyLabel =
                                  service.hierarchy.charAt(0).toUpperCase() +
                                  service.hierarchy.slice(1);
                                const label = `${service.name} (${hierarchyLabel})`;
                                return (
                                  <SelectItem
                                    key={`${service.hierarchy}:${service.name}`}
                                    value={service.name}
                                  >
                                    {label}
                                  </SelectItem>
                                );
                              })}
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
                        typeof field.value === 'string' &&
                        field.value.length > 0
                          ? (field.value as NpmAuthMode)
                          : undefined;
                      return (
                        <FormItem>
                          <FormLabel>Authorization mode</FormLabel>
                          <Select
                            value={selectedValue}
                            onValueChange={(value) => {
                              field.onChange(value);
                            }}
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
                </CardContent>
              </Card>
            )}
          </div>
          <PackageManagerField form={form} />
          {isSuperuser && (
            <FormField
              control={form.control}
              name='jobConfigs.analyzer.keepAliveWorker'
              render={({ field }) => (
                <FormItem className='mb-4 flex flex-row items-center justify-between rounded-lg border p-4'>
                  <div className='space-y-0.5'>
                    <FormLabel>Keep worker alive</FormLabel>
                    <FormDescription>
                      A flag to control whether the worker is kept alive for
                      debugging purposes. This flag only has an effect if the
                      ORT Server is deployed on Kubernetes.
                    </FormDescription>
                  </div>
                  <FormControl>
                    <Switch
                      checked={field.value}
                      onCheckedChange={field.onChange}
                    />
                  </FormControl>
                </FormItem>
              )}
            />
          )}
        </AccordionContent>
      </AccordionItem>
    </div>
  );
};
