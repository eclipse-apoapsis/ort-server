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
import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { Loader2 } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import {
  useOrganizationsServiceDeleteApiV1OrganizationsByOrganizationId,
  UseOrganizationsServiceGetApiV1OrganizationsByOrganizationIdKeyFn,
  useOrganizationsServicePatchApiV1OrganizationsByOrganizationId,
} from '@/api/queries';
import { useOrganizationsServiceGetApiV1OrganizationsByOrganizationIdSuspense } from '@/api/queries/suspense';
import { ApiError, OrganizationsService } from '@/api/requests';
import { DeleteDialog } from '@/components/delete-dialog';
import { ToastError } from '@/components/toast-error';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
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

const OrganizationSettingsPage = () => {
  const params = Route.useParams();
  const navigate = useNavigate();

  const organizationId = Number.parseInt(params.orgId);

  const { data: organization } =
    useOrganizationsServiceGetApiV1OrganizationsByOrganizationIdSuspense({
      organizationId,
    });

  const { mutateAsync, isPending } =
    useOrganizationsServicePatchApiV1OrganizationsByOrganizationId({
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
      description: organization.description || '',
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

  const { mutateAsync: deleteOrganization } =
    useOrganizationsServiceDeleteApiV1OrganizationsByOrganizationId({
      onSuccess() {
        toast.info('Delete Organization', {
          description: `Organization "${organization.name}" deleted successfully.`,
        });
        navigate({
          to: '/',
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

  async function handleDelete() {
    await deleteOrganization({
      organizationId: organizationId,
    });
  }

  return (
    <div className='flex flex-col gap-8'>
      <Card>
        <CardHeader>
          <CardTitle>Edit organization</CardTitle>
        </CardHeader>
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
                      <Input {...field} />
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
                onClick={() =>
                  navigate({
                    to: '/',
                  })
                }
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
      <Card>
        <CardHeader>
          <CardTitle>Danger Zone</CardTitle>
        </CardHeader>
        <CardContent>
          <div className='flex justify-between'>
            <div>Delete this organization</div>
            <DeleteDialog
              thingName={'organization'}
              thingId={organization.name}
              uiComponent={
                <Button variant='destructive'>Delete organization</Button>
              }
              onDelete={handleDelete}
            />
          </div>
        </CardContent>
      </Card>
    </div>
  );
};

export const Route = createFileRoute('/organizations/$orgId/settings/')({
  loader: async ({ context, params }) => {
    const organizationId = Number.parseInt(params.orgId);
    await context.queryClient.prefetchQuery({
      queryKey:
        UseOrganizationsServiceGetApiV1OrganizationsByOrganizationIdKeyFn({
          organizationId,
        }),
      queryFn: () =>
        OrganizationsService.getApiV1OrganizationsByOrganizationId({
          organizationId,
        }),
    });
  },
  component: OrganizationSettingsPage,
});
