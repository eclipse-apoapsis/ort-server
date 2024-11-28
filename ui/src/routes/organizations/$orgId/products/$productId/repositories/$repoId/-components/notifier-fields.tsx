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
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { CreateRunFormValues } from '../_repo-layout/-create-run-utils';

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
    name: 'jobConfigs.notifier.mail.recipientAddresses',
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
          <FormField
            control={form.control}
            name='jobConfigs.notifier.notifierRules'
            render={({ field }) => (
              <FormItem>
                <FormLabel>Notifier rules</FormLabel>
                <FormControl>
                  <Input {...field} />
                </FormControl>
                <FormDescription>
                  The notifier script to use. If this is not specified, the
                  configured default notification will be used.
                </FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name='jobConfigs.notifier.resolutionsFile'
            render={({ field }) => (
              <FormItem className='pt-4'>
                <FormLabel>Resolutions</FormLabel>
                <FormControl>
                  <Input {...field} />
                </FormControl>
                <FormDescription>
                  The path to the resolutions file which is resolved from the
                  configured configuration source. If this is not specified, the
                  default path from ORT will be used.
                </FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />

          <div className='pt-4'>
            <Label className='font-semibold text-blue-400'>
              Mail configuration
            </Label>
          </div>

          <div className='pl-4 pt-4'>
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
                    name={`jobConfigs.notifier.mail.recipientAddresses.${index}.email`}
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

            <div className='pt-6'>
              <Label className='font-semibold'>Mail server</Label>
            </div>

            <FormField
              control={form.control}
              name='jobConfigs.notifier.mail.mailServerConfiguration.hostName'
              render={({ field }) => (
                <FormItem className='pt-4'>
                  <FormLabel>Hostname</FormLabel>
                  <FormControl>
                    <Input {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='jobConfigs.notifier.mail.mailServerConfiguration.port'
              render={({ field }) => (
                <FormItem className='pt-4'>
                  <FormLabel>Port</FormLabel>
                  <FormControl>
                    <Input {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='jobConfigs.notifier.mail.mailServerConfiguration.username'
              render={({ field }) => (
                <FormItem className='pt-4'>
                  <FormLabel>Username</FormLabel>
                  <FormControl>
                    <Input {...field} />
                  </FormControl>
                  <FormDescription>
                    The path to the mail server username in the secrets
                    provider.
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='jobConfigs.notifier.mail.mailServerConfiguration.password'
              render={({ field }) => (
                <FormItem className='pt-4'>
                  <FormLabel>Password</FormLabel>
                  <FormControl>
                    <Input {...field} />
                  </FormControl>
                  <FormDescription>
                    The path to the mail server password in the secrets
                    provider.
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='jobConfigs.notifier.mail.mailServerConfiguration.useSsl'
              render={({ field }) => (
                <FormItem className='my-4 flex flex-row items-center justify-between rounded-lg border p-4'>
                  <div className='space-y-0.5'>
                    <FormLabel>Use SSL</FormLabel>
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
              name='jobConfigs.notifier.mail.mailServerConfiguration.fromAddress'
              render={({ field }) => (
                <FormItem className='pt-4'>
                  <FormLabel>From</FormLabel>
                  <FormControl>
                    <Input {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>

          <div className='pt-4'>
            <Label className='font-semibold text-blue-400'>
              Jira configuration
            </Label>
          </div>

          <div className='pl-4 pt-4'>
            <FormField
              control={form.control}
              name='jobConfigs.notifier.jira.jiraRestClientConfiguration.serverUrl'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Server URL</FormLabel>
                  <FormControl>
                    <Input {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='jobConfigs.notifier.jira.jiraRestClientConfiguration.username'
              render={({ field }) => (
                <FormItem className='pt-4'>
                  <FormLabel>Username</FormLabel>
                  <FormControl>
                    <Input {...field} />
                  </FormControl>
                  <FormDescription>
                    The path to the Jira server username in the secrets
                    provider.
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='jobConfigs.notifier.jira.jiraRestClientConfiguration.password'
              render={({ field }) => (
                <FormItem className='pt-4'>
                  <FormLabel>Password</FormLabel>
                  <FormControl>
                    <Input {...field} />
                  </FormControl>
                  <FormDescription>
                    The path to the Jira server password in the secrets
                    provider.
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>
        </AccordionContent>
      </AccordionItem>
    </div>
  );
};
