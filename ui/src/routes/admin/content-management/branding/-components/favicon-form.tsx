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
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Loader2 } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import {
  getConfigByKeyOptions,
  getConfigByKeyQueryKey,
  setConfigByKeyMutation,
} from '@/api/@tanstack/react-query.gen';
import { LoadingIndicator } from '@/components/loading-indicator.tsx';
import { ToastError } from '@/components/toast-error.tsx';
import { Button } from '@/components/ui/button.tsx';
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card.tsx';
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Switch } from '@/components/ui/switch';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip.tsx';
import { ApiError } from '@/lib/api-error';
import { toast } from '@/lib/toast.ts';

// In both URL fields, a valid URL or an empty string is accepted
const formSchema = z.object({
  faviconUrl: z.url().or(z.literal('')),
  isEnabled: z.boolean(),
});

export function FaviconForm() {
  const queryClient = useQueryClient();

  const {
    data: dbFavicon,
    isFetching: isFaviconFetching,
    isError: isFaviconError,
    error: faviconError,
  } = useQuery({
    ...getConfigByKeyOptions({ path: { key: 'FAVICON_URL' } }),
  });

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    values: {
      faviconUrl: dbFavicon?.value || '',
      isEnabled: dbFavicon?.isEnabled || false,
    },
  });

  const { mutateAsync: saveFavicon, isPending: isFaviconPending } = useMutation(
    {
      ...setConfigByKeyMutation(),
      onSuccess() {
        queryClient.invalidateQueries({
          queryKey: getConfigByKeyQueryKey({ path: { key: 'FAVICON_URL' } }),
        });
        toast.info('Favicon saved', {
          description: `The favicon was saved successfully.`,
        });
      },
      onError(error: ApiError) {
        toast.error(error.message, {
          description: <ToastError error={error} />,
          duration: Infinity,
          cancel: {
            label: 'Dismiss',
            onClick: () => {},
          },
        });
      },
    }
  );

  async function onSubmit(values: z.infer<typeof formSchema>) {
    await saveFavicon({
      body: {
        isEnabled: values.isEnabled,
        value: values.faviconUrl,
      },
      path: {
        key: 'FAVICON_URL',
      },
    });
  }

  if (isFaviconFetching) {
    return <LoadingIndicator />;
  }

  if (isFaviconError) {
    toast.error('Unable to load data', {
      description: <ToastError error={faviconError} />,
      duration: Infinity,
      cancel: {
        label: 'Dismiss',
        onClick: () => {},
      },
    });
    return;
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Favicon</CardTitle>
        <CardDescription>
          Customise the favicon by providing URL to icon image. The toggle
          specifies whether the default ORT Server favicon should be used or the
          icon from the provided URL.
        </CardDescription>
      </CardHeader>
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className='space-y-8'>
          <CardContent className='space-y-4'>
            <FormField
              control={form.control}
              name='faviconUrl'
              render={({ field }) => (
                <FormItem>
                  <FormControl>
                    <Input {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='isEnabled'
              render={({ field }) => (
                <FormItem className='flex items-center gap-2'>
                  <FormControl>
                    <Switch
                      checked={field.value}
                      onCheckedChange={field.onChange}
                    />
                  </FormControl>
                  <FormLabel>Enable customization</FormLabel>
                  <FormMessage />
                </FormItem>
              )}
            />
          </CardContent>
          <CardFooter className='flex gap-2'>
            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  type='button'
                  variant='outline'
                  onClick={() => form.reset()}
                >
                  Reset
                </Button>
              </TooltipTrigger>
              <TooltipContent>
                Revert to the saved (static) favicon and clear unsaved edits.
              </TooltipContent>
            </Tooltip>
            <Button type='submit' disabled={isFaviconPending}>
              {isFaviconPending ? (
                <>
                  <span className='sr-only'>Saving ...</span>
                  <Loader2 size={16} className='mx-3 animate-spin' />
                </>
              ) : (
                'Save'
              )}
            </Button>
          </CardFooter>
        </form>
      </Form>
    </Card>
  );
}
