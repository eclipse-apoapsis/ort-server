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
  useAdminServiceGetApiV1AdminConfigByKey,
  useAdminServiceGetApiV1AdminConfigByKeyKey,
  useAdminServicePostApiV1AdminConfigByKey,
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
import { Input } from '@/components/ui/input';
import { Switch } from '@/components/ui/switch';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip.tsx';
import { toast } from '@/lib/toast.ts';

const formSchema = z.object({
  productName: z.string().min(1, 'Product name is required'),
  isEnabled: z.boolean(),
});

export function ProductNameForm() {
  const queryClient = useQueryClient();

  const {
    data: dbProductName,
    isFetching,
    isError,
    error,
  } = useAdminServiceGetApiV1AdminConfigByKey({
    key: 'MAIN_PRODUCT_NAME',
  });

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      productName: dbProductName?.value || '',
      isEnabled: dbProductName?.isEnabled || false,
    },
  });

  const { mutateAsync, isPending } = useAdminServicePostApiV1AdminConfigByKey({
    onSuccess() {
      queryClient.invalidateQueries({
        queryKey: [useAdminServiceGetApiV1AdminConfigByKeyKey],
      });
      toast.info('Product name saved', {
        description: `Product name saved successfully.`,
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
        value: values.productName,
      },
      key: 'MAIN_PRODUCT_NAME',
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
        <CardTitle>Product Name</CardTitle>
        <CardDescription>
          Customize the product name. The toggle specifies whether the default
          product name "ORT Server" should be used or the specified product
          name.
        </CardDescription>
      </CardHeader>
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className='space-y-8'>
          <CardContent className='space-y-4'>
            <FormField
              control={form.control}
              name='productName'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Product Name</FormLabel>
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
                  <FormLabel>Use the provided product name</FormLabel>
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
                Revert to the saved product name and clear unsaved edits.
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
