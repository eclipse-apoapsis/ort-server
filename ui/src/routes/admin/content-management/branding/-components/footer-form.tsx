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
import { useQueryClient } from '@tanstack/react-query';
import { Loader2 } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import {
  useAdminServiceGetApiV1AdminContentManagementSectionsBySectionId,
  useAdminServiceGetApiV1AdminContentManagementSectionsBySectionIdKey,
  useAdminServicePatchApiV1AdminContentManagementSectionsBySectionId,
} from '@/api/queries';
import { ApiError } from '@/api/requests';
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
import { Switch } from '@/components/ui/switch';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip.tsx';
import { toast } from '@/lib/toast.ts';

const formSchema = z.object({
  markdown: z.string().min(1, 'Markdown content is required'),
  isEnabled: z.boolean(),
});

export function FooterForm() {
  const queryClient = useQueryClient();

  const {
    data: contentManagementSection,
    isFetching,
    error,
    isError,
  } = useAdminServiceGetApiV1AdminContentManagementSectionsBySectionId({
    sectionId: 'footer',
  });

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      markdown: contentManagementSection?.markdown || '',
      isEnabled: contentManagementSection?.isEnabled || false,
    },
  });

  const { mutateAsync, isPending } =
    useAdminServicePatchApiV1AdminContentManagementSectionsBySectionId({
      onSuccess() {
        queryClient.invalidateQueries({
          queryKey: [
            useAdminServiceGetApiV1AdminContentManagementSectionsBySectionIdKey,
          ],
        });
        toast.info('Footer saved', {
          description: `Footer saved successfully.`,
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
    });

  async function onSubmit(values: z.infer<typeof formSchema>) {
    await mutateAsync({
      requestBody: {
        isEnabled: values.isEnabled,
        markdown: values.markdown,
      },
      sectionId: 'footer',
    });
  }

  if (isFetching) {
    return <LoadingIndicator />;
  }

  if (isError) {
    toast.error('Unable to load data', {
      description: <ToastError error={error} />,
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
        <CardTitle>Footer</CardTitle>
        <CardDescription>
          Use Markdown to customize the footer content, and toggle the footer
          section on or off.
        </CardDescription>
      </CardHeader>
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className='space-y-8'>
          <CardContent className='space-y-4'>
            <FormField
              control={form.control}
              name='markdown'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Content</FormLabel>
                  <FormControl>
                    <textarea
                      placeholder='Write some Markdown...'
                      rows={25}
                      className='border-input bg-muted/50 min-h-[200px] w-full rounded-md border p-2 font-mono text-sm'
                      {...field}
                    />
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
                  <FormLabel>Show footer</FormLabel>
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
                Revert to the saved footer content and clear unsaved edits.
              </TooltipContent>
            </Tooltip>
            <Button type='submit' disabled={isPending}>
              {isPending ? (
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
