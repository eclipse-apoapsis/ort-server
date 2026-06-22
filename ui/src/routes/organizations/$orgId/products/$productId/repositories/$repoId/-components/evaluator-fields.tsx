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
import { PluginMultiSelectField } from '@/components/form/plugin-multi-select-field.tsx';
import {
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion.tsx';
import {
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
} from '@/components/ui/form';
import { Switch } from '@/components/ui/switch';
import { CreateRunFormValues } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/create-run/-components';

type EvaluatorFieldsProps = {
  form: UseFormReturn<CreateRunFormValues>;
  value: string;
  onToggle: () => void;
  isSuperuser: boolean;
  packageConfigurationProviderPlugins: PreconfiguredPluginDescriptor[];
  secrets: Secret[];
  isRerun: boolean;
};

export const EvaluatorFields = ({
  form,
  value,
  onToggle,
  isSuperuser,
  packageConfigurationProviderPlugins,
  secrets,
  isRerun,
}: EvaluatorFieldsProps) => {
  return (
    <div className='flex flex-row align-middle'>
      <FormField
        control={form.control}
        name='jobConfigs.evaluator.enabled'
        render={({ field }) => (
          <FormControl>
            <>
              <Switch
                className='my-4 mr-4 data-[state=checked]:bg-green-500'
                checked={field.value}
                onCheckedChange={field.onChange}
              />
            </>
          </FormControl>
        )}
      />
      <AccordionItem value={value} className='flex-1'>
        <AccordionTrigger onClick={onToggle}>Evaluator</AccordionTrigger>
        <AccordionContent className='flex flex-col gap-6'>
          <PluginMultiSelectField
            form={form}
            name='jobConfigs.evaluator.packageConfigurationProviders'
            configName='jobConfigs.evaluator.packageConfigurationProviderConfig'
            label='Package configuration providers'
            description={
              <>
                Configure the package configuration providers to use. Providers
                higher in the list take precedence over lower providers. Change
                the order of providers via drag & drop.
              </>
            }
            plugins={packageConfigurationProviderPlugins}
            secrets={secrets}
            enableReordering
            showSelectedPluginsFirst={isRerun}
          />
          {isSuperuser && (
            <FormField
              control={form.control}
              name='jobConfigs.evaluator.keepAliveWorker'
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
