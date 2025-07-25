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

import { PreconfiguredPluginDescriptor } from '@/api/requests';
import { MultiSelectField } from '@/components/form/multi-select-field';
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
import { CreateRunFormValues } from '../_repo-layout/create-run/-create-run-utils';

type ReporterFieldsProps = {
  form: UseFormReturn<CreateRunFormValues>;
  value: string;
  onToggle: () => void;
  reporterPlugins: PreconfiguredPluginDescriptor[];
};

export const ReporterFields = ({
  form,
  value,
  onToggle,
  reporterPlugins,
}: ReporterFieldsProps) => {
  const reporterOptions = reporterPlugins.map((plugin) => ({
    id: plugin.id,
    label: plugin.displayName,
    description: plugin.description,
  }));

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
        <AccordionContent>
          <MultiSelectField
            form={form}
            name='jobConfigs.reporter.formats'
            label='Report formats'
            description={
              <>Select the report formats to generate from the run.</>
            }
            options={reporterOptions}
          />
          {form.getValues('jobConfigs.reporter.formats').includes('WebApp') && (
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
        </AccordionContent>
      </AccordionItem>
    </div>
  );
};
