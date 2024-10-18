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
import { useEffect } from 'react';
import { useFieldArray, UseFormReturn } from 'react-hook-form';

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
  useEffect(() => {
    console.log(
      JSON.stringify(
        form.getValues('jobConfigs.analyzer.enabledPackageManagers'),
        null,
        2
      )
    );
  }, [form.getValues()]);

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
                    .map((pm) => pm.id)
                    .includes(option.id)
                )
                  ? true
                  : packageManagers.some((option) =>
                        form
                          .getValues(
                            'jobConfigs.analyzer.enabledPackageManagers'
                          )
                          .map((pm) => pm.id)
                          .includes(option.id)
                      )
                    ? 'indeterminate'
                    : false
              }
              onCheckedChange={(checked) => {
                const enabledItems = checked
                  ? packageManagers.map((option) => ({ id: option.id }))
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
          {packageManagers.map((pm, index) => (
            <FieldWithOptions
              form={form}
              key={pm.id}
              pmIndex={index}
              field={field}
            />
          ))}
          <FormMessage />
        </FormItem>
      )}
    />
  );
};

type FieldWithOptionsProps = {
  form: UseFormReturn<CreateRunFormValues>;
  field: {
    value: {
      id: string;
    }[];
    onChange: (value: { id: string }[]) => void;
  };
  pmIndex: number;
};

const FieldWithOptions = ({ form, field, pmIndex }: FieldWithOptionsProps) => {
  const {
    fields: optionsFields,
    append,
    remove,
  } = useFieldArray({
    name: `jobConfigs.analyzer.enabledPackageManagers.${pmIndex}.options`,
    control: form.control,
  });

  const pm = packageManagers[pmIndex];

  return (
    <FormItem
      key={pm.id}
      className='flex flex-row items-center space-x-3 space-y-0'
    >
      <FormControl>
        <Checkbox
          checked={field.value?.some((value) => value.id === pm.id)}
          onCheckedChange={(checked) => {
            return checked
              ? field.onChange([...field.value, { id: pm.id }])
              : field.onChange(
                  field.value?.filter((value) => value.id !== pm.id)
                );
          }}
        />
      </FormControl>

      {field.value.some((value) => value.id === pm.id) ? (
        <Accordion type='single' collapsible className='w-full'>
          <AccordionItem value='item' className='border-none'>
            <AccordionTrigger className='py-0 hover:no-underline'>
              <FormLabel className='font-normal'>{pm.label}</FormLabel>
            </AccordionTrigger>
            <AccordionContent>
              <h4 className='mt-2'>Options:</h4>
              {optionsFields.map((field, index) => (
                <div
                  key={field.id}
                  className='my-2 flex flex-row items-end space-x-2'
                >
                  <div className='flex-auto'>
                    {index === 0 && <FormLabel>Key</FormLabel>}
                    <FormField
                      control={form.control}
                      name={`jobConfigs.analyzer.enabledPackageManagers.${pmIndex}.options.${index}.key`}
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
                      name={`jobConfigs.analyzer.enabledPackageManagers.${pmIndex}.options.${index}.value`}
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
                      remove(index);
                    }}
                  >
                    <TrashIcon className='h-4 w-4' />
                  </Button>
                </div>
              ))}
              <Button
                size='sm'
                className='mt-2'
                variant='outline'
                type='button'
                onClick={() => {
                  append({ key: '', value: '' });
                }}
              >
                Add parameter
                <PlusIcon className='ml-1 h-4 w-4' />
              </Button>
            </AccordionContent>
          </AccordionItem>
        </Accordion>
      ) : (
        <FormLabel className='font-normal'>{pm.label}</FormLabel>
      )}
    </FormItem>
  );
};
