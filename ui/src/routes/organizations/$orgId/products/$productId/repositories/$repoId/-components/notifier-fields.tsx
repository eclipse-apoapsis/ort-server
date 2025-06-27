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
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { CreateRunFormValues } from '../_repo-layout/create-run/-create-run-utils';

type NotifierFieldsProps = {
  form: UseFormReturn<CreateRunFormValues>;
  value: string;
  onToggle: () => void;
};

export const NotifierFields = ({
  form,
  value,
  onToggle,
}: NotifierFieldsProps) => {
  const { fields, append, remove } = useFieldArray({
    name: 'jobConfigs.notifier.recipientAddresses',
    control: form.control,
  });

  return (
    <div className='flex flex-row align-middle'>
      <FormField
        control={form.control}
        name='jobConfigs.notifier.enabled'
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
        <AccordionTrigger onClick={onToggle}>Notifier</AccordionTrigger>
        <AccordionContent>
          <div>
            <Label className='font-semibold'>Recipient addresses</Label>
          </div>
          {fields.map((field, index) => (
            <div
              key={field.id}
              className='my-2 flex flex-row items-end space-x-2'
            >
              <div className='flex-auto'>
                {index === 0 && <FormLabel>E-mail address</FormLabel>}
                <FormField
                  control={form.control}
                  name={`jobConfigs.notifier.recipientAddresses.${index}.email`}
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
              append({ email: '' });
            }}
          >
            Add recipient address
            <PlusIcon className='ml-1 h-4 w-4' />
          </Button>
        </AccordionContent>
      </AccordionItem>
    </div>
  );
};
