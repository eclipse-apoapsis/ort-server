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
import { useMutation, useSuspenseQuery } from '@tanstack/react-query';
import {
  createFileRoute,
  useNavigate,
  useRouter,
} from '@tanstack/react-router';
import { Loader2 } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

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
import {
  deleteOrganizationByIdMutation,
  getOrganizationByIdOptions,
  patchOrganizationByIdMutation,
} from '@/hey-api/@tanstack/react-query.gen';
import { ApiError } from '@/lib/api-error';
import { toast } from '@/lib/toast';

const formSchema = z.object({
  name: z.string().min(1),
  description: z.string().optional(),
});

const OrganizationSettingsPage = () => {
  const params = Route.useParams();
  const navigate = useNavigate();
  const router = useRouter();

  const organizationId = Number.parseInt(params.orgId);

  const { data: organization } = useSuspenseQuery({
    ...getOrganizationByIdOptions({
      path: {
        organizationId,
      },
    }),
  });

  const { mutateAsync, isPending } = useMutation({
    ...patchOrganizationByIdMutation(),
    onSuccess(data) {
      toast.info('Edit Organization', {
        description: `Organization "${data.name}" updated successfully.`,
      });
      router.invalidate();
      navigate({
        to: '/organizations/$orgId',
        params: { orgId: params.orgId },
        reloadDocument: true,
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
      path: {
        organizationId: organization.id,
      },
      body: {
        name: values.name,
        description: values.description,
      },
    });
  }

  const { mutateAsync: deleteOrganization } = useMutation({
    ...deleteOrganizationByIdMutation(),
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
      path: {
        organizationId: organizationId,
      },
    });
  }

  return (
    <div className='flex flex-col gap-8'>
      <Card>
        <CardHeader>
          <CardTitle>Edit Organization</CardTitle>
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
  loader: async ({ context: { queryClient }, params }) => {
    const organizationId = Number.parseInt(params.orgId);
    await queryClient.prefetchQuery({
      ...getOrganizationByIdOptions({
        path: {
          organizationId,
        },
      }),
    });
  },
  component: OrganizationSettingsPage,
});
