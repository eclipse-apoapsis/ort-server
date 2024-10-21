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

import { UseFormReturn } from 'react-hook-form';

import {
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion';
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
import { CreateRunFormValues } from '../-create-run-utils';
import { PackageManagerField } from './package-manager-field';

type AnalyzerFieldsProps = {
  form: UseFormReturn<CreateRunFormValues>;
};

export const AnalyzerFields = ({ form }: AnalyzerFieldsProps) => {
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
      <AccordionItem value='analyzer' className='flex-1'>
        <AccordionTrigger>Analyzer</AccordionTrigger>
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
          <PackageManagerField form={form} />
        </AccordionContent>
      </AccordionItem>
    </div>
  );
};
