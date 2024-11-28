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

import { zodResolver } from '@hookform/resolvers/zod';
import { useSuspenseQuery } from '@tanstack/react-query';
import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { Loader2 } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import {
  useOrganizationsServiceGetOrganizationByIdKey,
  useOrganizationsServicePatchOrganizationById,
} from '@/api/queries';
import { ApiError, OrganizationsService } from '@/api/requests';
import { ToastError } from '@/components/toast-error';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardFooter,
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
import { Input } from '@/components/ui/input';
import { toast } from '@/lib/toast';

const formSchema = z.object({
  name: z.string(),
  description: z.string().optional(),
});

const EditOrganizationPage = () => {
  const params = Route.useParams();
  const navigate = useNavigate();

  const { data: organization } = useSuspenseQuery({
    queryKey: [useOrganizationsServiceGetOrganizationByIdKey, params.orgId],
    queryFn: async () =>
      await OrganizationsService.getOrganizationById({
        organizationId: Number.parseInt(params.orgId),
      }),
  });

  const { mutateAsync, isPending } =
    useOrganizationsServicePatchOrganizationById({
      onSuccess(data) {
        toast.info('Edit Organization', {
          description: `Organization "${data.name}" updated successfully.`,
        });
        navigate({
          to: '/organizations/$orgId',
          params: { orgId: params.orgId },
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

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      name: organization.name,
      description: organization.description ?? undefined,
    },
  });

  async function onSubmit(values: z.infer<typeof formSchema>) {
    await mutateAsync({
      organizationId: organization.id,
      requestBody: {
        name: values.name,
        description: values.description,
      },
    });
  }

  return (
    <Card>
      <CardHeader>Edit Organization</CardHeader>
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className='space-y-8'>
          <CardContent className='space-y-4'>
            <FormField
              control={form.control}
              name='name'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Name</FormLabel>
                  <FormControl autoFocus>
                    <Input {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='description'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Description</FormLabel>
                  <FormControl>
                    <Input {...field} placeholder='(optional)' />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </CardContent>
          <CardFooter>
            <Button
              type='button'
              className='m-1'
              variant='outline'
              onClick={() => navigate({ to: '/organizations/' + params.orgId })}
              disabled={isPending}
            >
              Cancel
            </Button>
            <Button type='submit' disabled={isPending}>
              {isPending ? (
                <>
                  <span className='sr-only'>Editing organization...</span>
                  <Loader2 size={16} className='mx-3 animate-spin' />
                </>
              ) : (
                'Submit'
              )}
            </Button>
          </CardFooter>
        </form>
      </Form>
    </Card>
  );
};

export const Route = createFileRoute('/organizations/$orgId/edit')({
  loader: async ({ context, params }) => {
    await context.queryClient.ensureQueryData({
      queryKey: [useOrganizationsServiceGetOrganizationByIdKey, params.orgId],
      queryFn: () =>
        OrganizationsService.getOrganizationById({
          organizationId: Number.parseInt(params.orgId),
        }),
    });
  },
  component: EditOrganizationPage,
});
