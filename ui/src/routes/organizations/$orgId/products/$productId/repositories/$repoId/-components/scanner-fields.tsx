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
import { PluginMultiSelectField } from '@/components/form/plugin-multi-select-field';
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
import { jobConfigFields } from '@/lib/job-config-fields';
import {
  CreateRunFormValues,
  DefaultPluginSelections,
} from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/create-run/-components';

type ScannerFieldsProps = {
  form: UseFormReturn<CreateRunFormValues>;
  value: string;
  onToggle: () => void;
  scannerPlugins: PreconfiguredPluginDescriptor[];
  secrets: Secret[];
  isSuperuser: boolean;
  /** The plugins that are selected by default, to which the plugin lists can be reset, if known. */
  defaultPluginSelections?: DefaultPluginSelections;
};

export const ScannerFields = ({
  form,
  value,
  onToggle,
  scannerPlugins,
  secrets,
  isSuperuser,
  defaultPluginSelections,
}: ScannerFieldsProps) => {
  return (
    <div className='flex flex-row align-middle'>
      <FormField
        control={form.control}
        name='jobConfigs.scanner.enabled'
        render={({ field }) => (
          <FormItem>
            <FormControl>
              <Switch
                className='my-4 mr-4 data-[state=checked]:bg-green-500'
                checked={field.value}
                onCheckedChange={field.onChange}
              />
            </FormControl>
          </FormItem>
        )}
      />
      <AccordionItem value={value} className='flex-1'>
        <AccordionTrigger onClick={onToggle}>Scanner</AccordionTrigger>
        <AccordionContent className='flex flex-col gap-6'>
          <FormField
            control={form.control}
            name='jobConfigs.scanner.skipConcluded'
            render={({ field }) => (
              <FormItem className='flex flex-row items-center justify-between rounded-lg border p-4'>
                <div className='space-y-0.5'>
                  <FormLabel>
                    {jobConfigFields['scanner.skipConcluded'].label}
                  </FormLabel>
                  <FormDescription>
                    {jobConfigFields['scanner.skipConcluded'].description}
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
            name='jobConfigs.scanner.skipExcluded'
            render={({ field }) => (
              <FormItem className='flex flex-row items-center justify-between rounded-lg border p-4'>
                <div className='space-y-0.5'>
                  <FormLabel>
                    {jobConfigFields['scanner.skipExcluded'].label}
                  </FormLabel>
                  <FormDescription>
                    {jobConfigFields['scanner.skipExcluded'].description}
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
          <PluginMultiSelectField
            form={form}
            name='jobConfigs.scanner.scanners'
            defaultPluginIds={defaultPluginSelections?.scanners}
            defaultScannerScopes={defaultPluginSelections?.scannerScopes}
            configName='jobConfigs.scanner.config'
            scannerScopeName='jobConfigs.scanner.scannerScopes'
            label={jobConfigFields['scanner.scanners'].label}
            description={jobConfigFields['scanner.scanners'].description}
            plugins={scannerPlugins}
            secrets={secrets}
          />
          {isSuperuser && (
            <FormField
              control={form.control}
              name='jobConfigs.scanner.keepAliveWorker'
              render={({ field }) => (
                <FormItem className='mb-4 flex flex-row items-center justify-between rounded-lg border p-4'>
                  <div className='space-y-0.5'>
                    <FormLabel>
                      {jobConfigFields['scanner.keepAliveWorker'].label}
                    </FormLabel>
                    <FormDescription>
                      {jobConfigFields['scanner.keepAliveWorker'].description}
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
