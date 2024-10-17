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
import { string } from 'zod';

import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import {
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Separator } from '@/components/ui/separator';
import { cn } from '@/lib/utils';
import { packageManagers } from '@/routes/_layout/organizations/$orgId/products/$productId/repositories/$repoId/-types';
import { CreateRunFormValues } from '../-create-run-utils';

type PackageManagerFieldProps = {
  form: UseFormReturn<CreateRunFormValues>;
  className?: string;
};

export const PackageManagerField = ({
  form,
  className,
}: PackageManagerFieldProps) => {
  return (
    <FormField
      control={form.control}
      name='jobConfigs.analyzer.enabledPackageManagers'
      render={({ field }) => (
        <FormItem
          className={cn(
            'mb-4 flex flex-col justify-between rounded-lg border p-4',
            className
          )}
        >
          <FormLabel>Enabled package managers</FormLabel>
          <FormDescription className='pb-4'>
            {
              <>
                Select the package managers enabled for this ORT Run. Note that
                the 'Unmanaged' package manager is always enabled.
              </>
            }
          </FormDescription>
          <div className='flex items-center space-x-3'>
            <Checkbox
              id='check-all-items'
              checked={
                packageManagers.every((option) =>
                  form
                    .getValues('jobConfigs.analyzer.enabledPackageManagers')
                    .includes(option.id)
                )
                  ? true
                  : packageManagers.some((option) =>
                        form
                          .getValues(
                            'jobConfigs.analyzer.enabledPackageManagers'
                          )
                          .includes(option.id)
                      )
                    ? 'indeterminate'
                    : false
              }
              onCheckedChange={(checked) => {
                const enabledItems = checked
                  ? packageManagers.map((option) => option.id)
                  : [];
                form.setValue(
                  'jobConfigs.analyzer.enabledPackageManagers',
                  enabledItems
                );
              }}
            />
            <Label htmlFor='check-all-items' className='font-bold'>
              Enable/disable all
            </Label>
          </div>
          <Separator />
          {packageManagers.map((option) => (
            <FormItem
              key={option.id}
              className='flex flex-row items-start space-x-3 space-y-0'
            >
              <FormControl>
                <Checkbox
                  checked={field.value?.includes(option.id)}
                  onCheckedChange={(checked) => {
                    return checked
                      ? field.onChange([...field.value, option.id])
                      : field.onChange(
                          field.value?.filter(
                            (value: string) => value !== option.id
                          )
                        );
                  }}
                />
              </FormControl>
              <FormLabel className='font-normal'>{option.label}</FormLabel>
            </FormItem>
          ))}
          <FormMessage />
        </FormItem>
      )}
    />
  );
};
