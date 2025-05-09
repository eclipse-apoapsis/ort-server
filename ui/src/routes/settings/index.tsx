/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import { zodResolver } from '@hookform/resolvers/zod';
import { createFileRoute } from '@tanstack/react-router';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
} from '@/components/ui/card';
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { Separator } from '@/components/ui/separator';
import { toast } from '@/lib/toast';
import { packageIdTypeSchema } from '@/schemas';
import { useUserSettingsStore } from '@/store/user-settings.store';

const formSchema = z.object({
  packageIdType: packageIdTypeSchema,
});

const SettingsPage = () => {
  const packageIdType = useUserSettingsStore((state) => state.packageIdType);
  const setPackageIdType = useUserSettingsStore(
    (state) => state.setPackageIdType
  );

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      packageIdType: packageIdType,
    },
  });

  async function onSubmit(values: z.infer<typeof formSchema>) {
    setPackageIdType(values.packageIdType);
    toast.info('Preferences', {
      description: `Preferences saved to browser cookies.`,
    });
  }

  return (
    <Card className='mx-auto w-full max-w-4xl'>
      <CardHeader>
        <h2 className='text-3xl font-bold tracking-tight'>Preferences</h2>
        <CardDescription>
          These user-specific choices are saved as cookies in the browser.
          Clearing browser cookies will result in preferences to be reset to
          their defaults.
        </CardDescription>
      </CardHeader>
      <Separator />
      <CardContent>
        <Form {...form}>
          <form
            onSubmit={form.handleSubmit(onSubmit)}
            className='mt-4 space-y-8'
          >
            <FormField
              control={form.control}
              name='packageIdType'
              render={({ field }) => (
                <FormItem className='space-y-3'>
                  <FormLabel>Package ID type</FormLabel>
                  <FormControl className='mt-2'>
                    <RadioGroup
                      onValueChange={field.onChange}
                      defaultValue={field.value}
                      className='ml-4 flex flex-col space-y-0'
                    >
                      <FormItem className='flex items-center space-y-0 space-x-3'>
                        <FormControl>
                          <RadioGroupItem value='ORT_ID' />
                        </FormControl>
                        <FormLabel className='font-normal'>ORT ID</FormLabel>
                      </FormItem>
                      <FormItem className='flex items-center space-y-0 space-x-3'>
                        <FormControl>
                          <RadioGroupItem value='PURL' />
                        </FormControl>
                        <FormLabel className='font-normal'>
                          Package URL (PURL)
                        </FormLabel>
                      </FormItem>
                    </RadioGroup>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <Button type='submit'>Save</Button>
          </form>
        </Form>
      </CardContent>
    </Card>
  );
};

export const Route = createFileRoute('/settings/')({
  component: SettingsPage,
});
