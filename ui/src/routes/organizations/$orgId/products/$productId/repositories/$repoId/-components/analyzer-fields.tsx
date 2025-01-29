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

import { PlusIcon, TrashIcon } from 'lucide-react';
import { useFieldArray, UseFormReturn } from 'react-hook-form';

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
import { CreateRunFormValues } from '../_repo-layout/create-run/-create-run-utils';
import { PackageManagerField } from './package-manager-field';

type AnalyzerFieldsProps = {
  form: UseFormReturn<CreateRunFormValues>;
  value: string;
  onToggle: () => void;
};

export const AnalyzerFields = ({
  form,
  value,
  onToggle,
}: AnalyzerFieldsProps) => {
  const {
    fields: environmentVariablesFields,
    append: environmentVariablesAppend,
    remove: environmentVariablesRemove,
  } = useFieldArray({
    name: 'jobConfigs.analyzer.environmentVariables',
    control: form.control,
  });

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
              A set of environment variables (name, value) that the analyzer
              needs to analyze the project.
            </div>
            {environmentVariablesFields.map((field, index) => (
              <div key={field.id} className='flex flex-row items-end space-x-2'>
                <div className='flex-auto'>
                  {index === 0 && <FormLabel>Name</FormLabel>}
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
                  {index === 0 && <FormLabel>Value</FormLabel>}
                  <FormField
                    control={form.control}
                    name={`jobConfigs.analyzer.environmentVariables.${index}.value`}
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
          <PackageManagerField form={form} />
        </AccordionContent>
      </AccordionItem>
    </div>
  );
};
