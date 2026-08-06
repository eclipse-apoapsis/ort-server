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
import { useEffect } from 'react';
import { useFieldArray, UseFormReturn } from 'react-hook-form';

import {
  InfrastructureService,
  PreconfiguredPluginDescriptor,
  Secret,
} from '@/api';
import { zAnalyzerPhase } from '@/api/zod.gen';
import { MultiSelectField } from '@/components/form/multi-select-field.tsx';
import { PluginMultiSelectField } from '@/components/form/plugin-multi-select-field.tsx';
import { SecretSelect } from '@/components/secret-select';
import { InlineCode } from '@/components/typography.tsx';
import {
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion';
import { Button } from '@/components/ui/button';
import {
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Switch } from '@/components/ui/switch';
import { capitalize } from '@/helpers/capitalize';
import { useInfrastructureServices } from '@/hooks/use-infrastructure-services.ts';
import {
  OrganizationPermissions,
  ProductPermissions,
  RepositoryPermissions,
} from '@/lib/permissions.ts';
import { cn } from '@/lib/utils';
import { CreateRunFormValues } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/create-run/-components';
import { EnvironmentDefinitionsFields } from './environment-definitions';
import { PackageManagerField } from './package-manager-field';

type AnalyzerFieldsProps = {
  form: UseFormReturn<CreateRunFormValues>;
  value: string;
  onToggle: () => void;
  isSuperuser: boolean;
  packageCurationProviderPlugins: PreconfiguredPluginDescriptor[];
  pluginSecrets: Secret[];
  isRerun: boolean;
  permissions: {
    organization: OrganizationPermissions | undefined;
    product: ProductPermissions | undefined;
    repository: RepositoryPermissions | undefined;
  };
};

export const AnalyzerFields = ({
  form,
  value,
  onToggle,
  isSuperuser,
  packageCurationProviderPlugins,
  pluginSecrets,
  isRerun,
  permissions,
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

  const infrastructureServices = useInfrastructureServices({
    orgId,
    productId,
    repoId,
    permissions,
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

  const keepAliveWorker = form.watch('jobConfigs.analyzer.keepAliveWorker');

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
                  is not defined, the repository configuration is read from
                  ".ort.yml" in the root of the analyzed project repository.
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
            <div className='text-sm text-gray-500'>
              A list of environment variable names and their values, either
              literal ones, or retrieved from named secrets. Use this to specify
              environment variables that are required by the build process. In
              case of Gradle, this can also be used to{' '}
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
            {(() => {
              const hasSecrets = environmentVariablesFields.some(
                (field) => 'secretName' in field
              );
              const hasValues = environmentVariablesFields.some(
                (field) => 'value' in field
              );
              const secondColumnLabel =
                hasSecrets && hasValues
                  ? 'Value / Secret'
                  : hasSecrets
                    ? 'Secret'
                    : 'Value';

              return environmentVariablesFields.map((field, index) => {
                const isSecret = 'secretName' in field;
                return (
                  <div key={field.id} className='flex flex-row space-x-2'>
                    <div className='flex-1'>
                      <FormField
                        control={form.control}
                        name={`jobConfigs.analyzer.environmentVariables.${index}.name`}
                        render={({ field }) => (
                          <FormItem>
                            {index === 0 && <FormLabel>Name</FormLabel>}
                            <FormControl>
                              <Input {...field} />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                    </div>
                    <div className='flex-1'>
                      {isSecret ? (
                        <FormField
                          control={form.control}
                          name={`jobConfigs.analyzer.environmentVariables.${index}.secretName`}
                          render={({ field }) => (
                            <FormItem>
                              {index === 0 && (
                                <FormLabel>{secondColumnLabel}</FormLabel>
                              )}
                              <SecretSelect
                                value={
                                  typeof field.value === 'string'
                                    ? field.value
                                    : undefined
                                }
                                onChange={field.onChange}
                                placeholder='Select a secret'
                                orgId={orgId}
                                productId={productId}
                                repositoryId={repoId}
                                permissions={permissions}
                                className='w-full'
                              />
                              <FormMessage />
                            </FormItem>
                          )}
                        />
                      ) : (
                        <FormField
                          control={form.control}
                          name={`jobConfigs.analyzer.environmentVariables.${index}.value`}
                          render={({ field }) => (
                            <FormItem>
                              {index === 0 && (
                                <FormLabel>{secondColumnLabel}</FormLabel>
                              )}
                              <FormControl>
                                <Input
                                  {...field}
                                  value={field.value ?? undefined}
                                />
                              </FormControl>
                              <FormMessage />
                            </FormItem>
                          )}
                        />
                      )}
                    </div>
                    <div className='flex items-end'>
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
                  </div>
                );
              });
            })()}
            <div className='flex gap-2'>
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
              <Button
                size='sm'
                className='mt-2 w-min'
                variant='outline'
                type='button'
                onClick={() => {
                  environmentVariablesAppend({ name: '', secretName: '' });
                }}
              >
                Add environment secret
                <PlusIcon className='ml-1 h-4 w-4' />
              </Button>
            </div>
          </div>
          <EnvironmentDefinitionsFields form={form} />
          <PackageManagerField form={form} />
          <PluginMultiSelectField
            form={form}
            name='jobConfigs.analyzer.packageCurationProviders'
            configName='jobConfigs.analyzer.packageCurationProviderConfig'
            label='Package curation providers'
            description={
              <>
                Configure the package curation providers to use. Providers
                higher in the list take precedence over lower providers. Change
                the order of providers via drag & drop.
              </>
            }
            plugins={packageCurationProviderPlugins}
            secrets={pluginSecrets}
            enableReordering
            showSelectedPluginsFirst={isRerun}
          />
          {isSuperuser && (
            <FormField
              control={form.control}
              name='jobConfigs.analyzer.keepAliveWorker'
              render={({ field }) => (
                <FormItem className='mb-4 rounded-lg border p-4'>
                  <div className='flex flex-row items-center justify-between'>
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
                  </div>
                  <div
                    className={cn(
                      'grid transition-[grid-template-rows,opacity] duration-400 ease-out',
                      keepAliveWorker
                        ? 'grid-rows-[1fr] opacity-100'
                        : 'invisible grid-rows-[0fr] opacity-0'
                    )}
                  >
                    <div className='mx-4 mt-4 overflow-hidden'>
                      <MultiSelectField
                        form={form}
                        name='jobConfigs.analyzer.keepAlivePhases'
                        label='Phases'
                        description={
                          <>
                            The phases in which the worker should be kept alive.
                            If none are selected, the worker is kept alive in
                            the <InlineCode>full</InlineCode> and{' '}
                            <InlineCode>analysis</InlineCode> phases.
                          </>
                        }
                        options={zAnalyzerPhase.options.map((phase) => ({
                          id: phase,
                          label: capitalize(phase),
                        }))}
                        className='mb-0 border-0 p-0'
                      />
                    </div>
                  </div>
                </FormItem>
              )}
            />
          )}
        </AccordionContent>
      </AccordionItem>
    </div>
  );
};
