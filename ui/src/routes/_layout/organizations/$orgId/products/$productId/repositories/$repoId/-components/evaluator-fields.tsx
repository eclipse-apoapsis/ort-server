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

type EvaluatorFieldsProps = {
  form: UseFormReturn<CreateRunFormValues>;
  value: string;
  onToggle: () => void;
};

export const EvaluatorFields = ({
  form,
  value,
  onToggle,
}: EvaluatorFieldsProps) => {
  return (
    <div className='flex flex-row align-middle'>
      <FormField
        control={form.control}
        name='jobConfigs.evaluator.enabled'
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
        <AccordionTrigger onClick={onToggle}>Evaluator</AccordionTrigger>
        <AccordionContent>
          <div className='text-sm text-gray-500'>
            In case any input field is left empty, the default path from the
            config file provider will be used for the corresponding file.
          </div>
          <FormField
            control={form.control}
            name='jobConfigs.evaluator.ruleSet'
            render={({ field }) => (
              <FormItem className='pt-4'>
                <FormLabel>Evaluator rules</FormLabel>
                <FormControl>
                  <Input {...field} />
                </FormControl>
                <FormDescription>
                  The path to the rules file to get from the configuration
                  provider.
                </FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name='jobConfigs.evaluator.licenseClassificationsFile'
            render={({ field }) => (
              <FormItem className='pt-4'>
                <FormLabel>License classifications</FormLabel>
                <FormControl>
                  <Input {...field} />
                </FormControl>
                <FormDescription>
                  The path to the license classifications file to get from the
                  configuration provider.
                </FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name='jobConfigs.evaluator.copyrightGarbageFile'
            render={({ field }) => (
              <FormItem className='pt-4'>
                <FormLabel>Copyright garbage</FormLabel>
                <FormControl>
                  <Input {...field} />
                </FormControl>
                <FormDescription>
                  The path to the copyright garbage file to get from the
                  configuration provider.
                </FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name='jobConfigs.evaluator.resolutionsFile'
            render={({ field }) => (
              <FormItem className='pt-4'>
                <FormLabel>Resolutions</FormLabel>
                <FormControl>
                  <Input {...field} />
                </FormControl>
                <FormDescription>
                  The path to the resolutions file to get from the configuration
                  provider.
                </FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />
        </AccordionContent>
      </AccordionItem>
    </div>
  );
};
