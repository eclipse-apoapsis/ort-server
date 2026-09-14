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
import {
  useMutation,
  useQueryClient,
  useSuspenseQuery,
} from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';
import { Loader2 } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import {
  getOrganizationOptions,
  getOrganizationUsersQueryKey,
  putOrganizationRoleToUserMutation,
} from '@/api/@tanstack/react-query.gen';
import { RoleSelect } from '@/components/role-select';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { ApiError } from '@/lib/api-error';
import { toast, toastError } from '@/lib/toast';
import { roleSchema } from '@/schemas';
import { OrganizationUsersTable } from './-components/organization-users-table';

const formSchema = z.object({
  username: z.string().trim().min(1),
  role: roleSchema,
});

const ManageUsers = () => {
  const params = Route.useParams();

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      username: '',
      role: 'READER',
    },
  });

  const { data: organization } = useSuspenseQuery({
    ...getOrganizationOptions({
      path: {
        organizationId: Number.parseInt(params.orgId),
      },
    }),
  });

  const queryClient = useQueryClient();

  const { mutateAsync: addUser, isPending: isAddUserPending } = useMutation({
    ...putOrganizationRoleToUserMutation(),
    onSuccess() {
      queryClient.invalidateQueries({
        queryKey: getOrganizationUsersQueryKey({
          path: {
            organizationId: Number.parseInt(params.orgId),
          },
        }),
      });
      toast.info('Add User', {
        description: `Successfully assigned role "${form.getValues().role}" to user "${form.getValues().username}".`,
      });
    },
    onError(error: ApiError) {
      toastError(error.message, error);
    },
  });

  async function onAddUser(values: z.infer<typeof formSchema>) {
    await addUser({
      path: {
        organizationId: Number.parseInt(params.orgId),
        role: values.role,
      },
      body: {
        username: values.username,
      },
    });

    form.setValue('username', '');
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Manage Users</CardTitle>
        <CardDescription>
          Assign or remove roles to users in the{' '}
          <span className='font-semibold'>{organization.name}</span>{' '}
          organization.
        </CardDescription>
      </CardHeader>
      <CardContent className='space-y-4'>
        <Form {...form}>
          <form className='space-y-8'>
            <div className='grid items-start gap-4 md:grid-cols-[minmax(0,1fr)_auto]'>
              <FormField
                control={form.control}
                name='username'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Username</FormLabel>
                    <FormControl autoFocus>
                      <Input {...field} />
                    </FormControl>
                    <FormDescription>
                      Enter the exact username of an existing user account.
                    </FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name='role'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Role</FormLabel>
                    <RoleSelect
                      value={field.value}
                      onChange={field.onChange}
                      level='organization'
                    />
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>
            <Button
              size='sm'
              className='ml-auto gap-1'
              type='submit'
              disabled={isAddUserPending}
              onClick={form.handleSubmit(onAddUser)}
            >
              {isAddUserPending ? (
                <>
                  <span className='sr-only'>Adding user...</span>
                  <Loader2 size={16} className='mx-3 animate-spin' />
                </>
              ) : (
                'Assign role'
              )}
            </Button>
          </form>
        </Form>
        <OrganizationUsersTable />
      </CardContent>
    </Card>
  );
};

export const Route = createFileRoute('/organizations/$orgId/users/')({
  component: ManageUsers,
});
