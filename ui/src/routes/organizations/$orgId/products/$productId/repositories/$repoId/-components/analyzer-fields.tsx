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
import { useFieldArray, UseFormReturn, useWatch } from 'react-hook-form';

import { PreconfiguredPluginDescriptor, Secret } from '@/api';
import { zAnalyzerPhase } from '@/api/zod.gen';
import { MultiSelectField } from '@/components/form/multi-select-field.tsx';
import { PluginMultiSelectField } from '@/components/form/plugin-multi-select-field';
import { SecretSelect } from '@/components/secret-select';
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
import { jobConfigFields } from '@/lib/job-config-fields';
import {
  OrganizationPermissions,
  ProductPermissions,
  RepositoryPermissions,
} from '@/lib/permissions.ts';
import { cn } from '@/lib/utils';
import {
  CreateRunFormValues,
  DefaultPluginSelections,
} from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/create-run/-components';
import { EnvironmentDefinitionsFields } from './environment-definitions';

const environmentVariables =
  jobConfigFields['analyzer.environmentConfig.environmentVariables'];
const nameField =
  jobConfigFields['analyzer.environmentConfig.environmentVariables.*.name'];
const valueField =
  jobConfigFields['analyzer.environmentConfig.environmentVariables.*.value'];
const secretField =
  jobConfigFields[
    'analyzer.environmentConfig.environmentVariables.*.secretName'
  ];

type AnalyzerFieldsProps = {
  form: UseFormReturn<CreateRunFormValues>;
  value: string;
  onToggle: () => void;
  isSuperuser: boolean;
  packageCurationProviderPlugins: PreconfiguredPluginDescriptor[];
  packageManagerPlugins: PreconfiguredPluginDescriptor[];
  pluginSecrets: Secret[];
  permissions: {
    organization: OrganizationPermissions | undefined;
    product: ProductPermissions | undefined;
    repository: RepositoryPermissions | undefined;
  };
  /** The plugins that are selected by default, to which the plugin lists can be reset, if known. */
  defaultPluginSelections?: DefaultPluginSelections;
};

export const AnalyzerFields = ({
  form,
  value,
  onToggle,
  isSuperuser,
  packageCurationProviderPlugins,
  packageManagerPlugins,
  pluginSecrets,
  permissions,
  defaultPluginSelections,
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

  const keepAliveWorker = useWatch({
    control: form.control,
    name: 'jobConfigs.analyzer.keepAliveWorker',
  });

  return (
    <div className='flex flex-row align-middle'>
      <FormField
        control={form.control}
        name='jobConfigs.analyzer.enabled'
        render={({ field }) => (
          <FormItem>
            <FormControl>
              <Switch
                className='my-4 mr-4 data-[state=checked]:bg-green-500'
                checked={field.value}
                disabled
                onCheckedChange={field.onChange}
              />
            </FormControl>
          </FormItem>
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
                <FormLabel>
                  {jobConfigFields['analyzer.repositoryConfigPath'].label}
                </FormLabel>
                <FormControl>
                  <Input {...field} placeholder='(optional)' />
                </FormControl>
                <FormDescription>
                  {jobConfigFields['analyzer.repositoryConfigPath'].description}
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
                  <FormLabel>
                    {jobConfigFields['analyzer.allowDynamicVersions'].label}
                  </FormLabel>
                  <FormDescription>
                    {
                      jobConfigFields['analyzer.allowDynamicVersions']
                        .description
                    }
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
                  <FormLabel>
                    {jobConfigFields['analyzer.skipExcluded'].label}
                  </FormLabel>
                  <FormDescription>
                    {jobConfigFields['analyzer.skipExcluded'].description}
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
            <h3>{environmentVariables.label}</h3>
            <div className='text-sm text-gray-500'>
              {environmentVariables.description}
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
                  ? `${valueField.label} / ${secretField.label}`
                  : hasSecrets
                    ? secretField.label
                    : valueField.label;

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
                            {index === 0 && (
                              <FormLabel>{nameField.label}</FormLabel>
                            )}
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
          <PluginMultiSelectField
            form={form}
            name='jobConfigs.analyzer.packageManagers'
            defaultPluginIds={defaultPluginSelections?.packageManagers}
            configName='jobConfigs.analyzer.packageManagerConfig'
            mustRunAfterName='jobConfigs.analyzer.packageManagerMustRunAfter'
            label={jobConfigFields['analyzer.enabledPackageManagers'].label}
            description={
              jobConfigFields['analyzer.enabledPackageManagers'].description
            }
            plugins={packageManagerPlugins}
            secrets={[]}
          />
          <PluginMultiSelectField
            form={form}
            name='jobConfigs.analyzer.packageCurationProviders'
            defaultPluginIds={defaultPluginSelections?.packageCurationProviders}
            configName='jobConfigs.analyzer.packageCurationProviderConfig'
            label={jobConfigFields['analyzer.packageCurationProviders'].label}
            description={
              jobConfigFields['analyzer.packageCurationProviders'].description
            }
            plugins={packageCurationProviderPlugins}
            secrets={pluginSecrets}
            enableReordering
          />
          {isSuperuser && (
            <FormField
              control={form.control}
              name='jobConfigs.analyzer.keepAliveWorker'
              render={({ field }) => (
                <FormItem className='mb-4 rounded-lg border p-4'>
                  <div className='flex flex-row items-center justify-between'>
                    <div className='space-y-0.5'>
                      <FormLabel>
                        {jobConfigFields['analyzer.keepAliveWorker'].label}
                      </FormLabel>
                      <FormDescription>
                        {
                          jobConfigFields['analyzer.keepAliveWorker']
                            .description
                        }
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
                        label={
                          jobConfigFields['analyzer.keepAlivePhases'].label
                        }
                        description={
                          jobConfigFields['analyzer.keepAlivePhases']
                            .description
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
