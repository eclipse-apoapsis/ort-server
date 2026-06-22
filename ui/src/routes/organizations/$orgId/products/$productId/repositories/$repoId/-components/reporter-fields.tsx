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

import { PreconfiguredPluginDescriptor, Secret } from '@/api';
import { MultiSelectField } from '@/components/form/multi-select-field';
import { PluginMultiSelectField } from '@/components/form/plugin-multi-select-field.tsx';
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
} from '@/components/ui/form';
import { Switch } from '@/components/ui/switch';
import { CreateRunFormValues } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/create-run/-components';

type ReporterFieldsProps = {
  form: UseFormReturn<CreateRunFormValues>;
  value: string;
  onToggle: () => void;
  reporterPlugins: PreconfiguredPluginDescriptor[];
  isSuperuser: boolean;
  packageConfigurationProviderPlugins: PreconfiguredPluginDescriptor[];
  secrets: Secret[];
  isRerun: boolean;
};

export const ReporterFields = ({
  form,
  value,
  onToggle,
  reporterPlugins,
  isSuperuser,
  packageConfigurationProviderPlugins,
  secrets,
  isRerun,
}: ReporterFieldsProps) => {
  const reporterOptions = reporterPlugins.map((plugin) => ({
    id: plugin.id,
    label: plugin.displayName,
    summary: plugin.summary,
  }));
  const evaluatorEnabled = form.watch('jobConfigs.evaluator.enabled');

  return (
    <div className='flex flex-row align-middle'>
      <FormField
        control={form.control}
        name='jobConfigs.reporter.enabled'
        render={({ field }) => (
          <FormControl>
            <Switch
              className='my-4 mr-4 data-[state=checked]:bg-green-500'
              checked={field.value}
              onCheckedChange={field.onChange}
            />
          </FormControl>
        )}
      />
      <AccordionItem value={value} className='flex-1'>
        <AccordionTrigger onClick={onToggle}>Reporter</AccordionTrigger>
        <AccordionContent className='flex flex-col gap-6'>
          <MultiSelectField
            form={form}
            name='jobConfigs.reporter.formats'
            label='Report formats'
            description={
              <>Select the report formats to generate from the run.</>
            }
            options={reporterOptions}
          />
          {!evaluatorEnabled && (
            <PluginMultiSelectField
              form={form}
              name='jobConfigs.reporter.packageConfigurationProviders'
              configName='jobConfigs.reporter.packageConfigurationProviderConfig'
              label='Package configuration providers'
              description={
                <>
                  Configure the package configuration providers to use.
                  Providers higher in the list take precedence over lower
                  providers. Change the order of providers via drag & drop.
                </>
              }
              plugins={packageConfigurationProviderPlugins}
              secrets={secrets}
              enableReordering
              showSelectedPluginsFirst={isRerun}
            />
          )}
          {form.watch('jobConfigs.reporter.formats').includes('WebApp') && (
            <FormField
              control={form.control}
              name='jobConfigs.reporter.deduplicateDependencyTree'
              render={({ field }) => (
                <FormItem className='mb-4 flex flex-row items-center justify-between rounded-lg border p-4'>
                  <div className='space-y-0.5'>
                    <FormLabel>Deduplicate dependency tree</FormLabel>
                    <FormDescription>
                      A flag to control whether subtrees occurring multiple
                      times in the dependency tree are stripped.
                    </FormDescription>
                    <FormDescription>
                      This will significantly reduce memory consumption of the
                      Reporter and might alleviate some out-of-memory issues.
                    </FormDescription>
                    <FormDescription>
                      NOTE: This option is currently effective only for the
                      WebApp report format.
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
          {isSuperuser && (
            <FormField
              control={form.control}
              name='jobConfigs.reporter.keepAliveWorker'
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
