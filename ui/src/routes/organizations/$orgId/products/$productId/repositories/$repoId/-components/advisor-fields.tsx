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

type AdvisorFieldsProps = {
  form: UseFormReturn<CreateRunFormValues>;
  value: string;
  onToggle: () => void;
  advisorPlugins: PreconfiguredPluginDescriptor[];
  isSuperuser: boolean;
};

export const AdvisorFields = ({
  form,
  value,
  onToggle,
  advisorPlugins,
  isSuperuser,
}: AdvisorFieldsProps) => {
  const advisorOptions = advisorPlugins.map((plugin) => ({
    id: plugin.id,
    label: plugin.displayName,
    description: plugin.description,
  }));

  return (
    <div className='flex flex-row align-middle'>
      <FormField
        control={form.control}
        name='jobConfigs.advisor.enabled'
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
        <AccordionTrigger onClick={onToggle}>Advisor</AccordionTrigger>
        <AccordionContent>
          <FormField
            control={form.control}
            name='jobConfigs.advisor.skipExcluded'
            render={({ field }) => (
              <FormItem className='mb-4 flex flex-row items-center justify-between rounded-lg border p-4'>
                <div className='space-y-0.5'>
                  <FormLabel>Skip excluded</FormLabel>
                  <FormDescription>
                    A flag to control whether excluded scopes and paths should
                    be skipped by the advisor.
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
          <MultiSelectField
            form={form}
            name='jobConfigs.advisor.advisors'
            label='Enabled advisors'
            description={<>Select the advisors enabled for this run.</>}
            options={advisorOptions}
          />
          {isSuperuser && (
            <FormField
              control={form.control}
              name='jobConfigs.advisor.keepAliveWorker'
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
