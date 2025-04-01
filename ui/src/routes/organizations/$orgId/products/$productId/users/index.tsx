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
import { createFileRoute } from '@tanstack/react-router';
import { Loader2 } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import {
  useGroupsServiceDeleteApiV1ProductsByProductIdGroupsByGroupId,
  useGroupsServicePutApiV1ProductsByProductIdGroupsByGroupId,
} from '@/api/queries';
import { prefetchUseProductsServiceGetApiV1ProductsByProductId } from '@/api/queries/prefetch';
import { useProductsServiceGetApiV1ProductsByProductIdSuspense } from '@/api/queries/suspense';
import { ApiError } from '@/api/requests';
import { ToastError } from '@/components/toast-error';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
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
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { toast } from '@/lib/toast';
import { groupsSchema } from '@/schemas';

const formSchema = z.object({
  username: z.string().min(1),
  groupId: groupsSchema,
});

const ManageUsers = () => {
  const params = Route.useParams();

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      username: '',
      groupId: 'readers',
    },
  });

  const { data: product } =
    useProductsServiceGetApiV1ProductsByProductIdSuspense({
      productId: Number.parseInt(params.productId),
    });

  const { mutateAsync: addUser, isPending: isAddUserPending } =
    useGroupsServicePutApiV1ProductsByProductIdGroupsByGroupId({
      onSuccess() {
        toast.info('Add User', {
          description: `User "${form.getValues().username}" added successfully to group "${form.getValues().groupId.toUpperCase()}".`,
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

  const { mutateAsync: removeUser, isPending: isRemoveUserPending } =
    useGroupsServiceDeleteApiV1ProductsByProductIdGroupsByGroupId({
      onSuccess() {
        toast.info('Remove User', {
          description: `User "${form.getValues().username}" removed successfully from group "${form.getValues().groupId.toUpperCase()}".`,
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

  async function onAddUser(values: z.infer<typeof formSchema>) {
    await addUser({
      productId: Number.parseInt(params.productId),
      groupId: values.groupId,
      requestBody: {
        username: values.username,
      },
    });
  }

  async function onRemoveUser(values: z.infer<typeof formSchema>) {
    await removeUser({
      productId: Number.parseInt(params.productId),
      groupId: values.groupId,
      requestBody: {
        username: values.username,
      },
    });
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Manage Users</CardTitle>
        <CardDescription className='flex flex-col gap-2'>
          <span>
            Assign or remove users to different groups in product:{' '}
            <span className='font-semibold'>{product.name}</span>
          </span>
          <span>
            READERS: Can view the product and its repositories.
            <br />
            WRITERS: Can view and edit the product and its repositories.
            <br />
            ADMINS: Can view, edit, and delete the product and its repositories.
          </span>
          <span>NOTE: The username must already exist in Keycloak.</span>
        </CardDescription>
      </CardHeader>
      <Form {...form}>
        <form className='space-y-8'>
          <CardContent className='space-y-4'>
            <FormField
              control={form.control}
              name='username'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Username</FormLabel>
                  <FormControl autoFocus>
                    <Input {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name='groupId'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Group</FormLabel>
                  <Select
                    onValueChange={field.onChange}
                    defaultValue={field.value}
                  >
                    <FormControl>
                      <SelectTrigger>
                        <SelectValue placeholder='Select a group' />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {groupsSchema.options.map((group) => (
                        <SelectItem key={group} value={group}>
                          {group.toUpperCase()}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )}
            />
          </CardContent>
          <CardFooter className='gap-2'>
            <Button
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
                'Add User'
              )}
            </Button>
            <Button
              type='submit'
              disabled={isRemoveUserPending}
              onClick={form.handleSubmit(onRemoveUser)}
            >
              {isRemoveUserPending ? (
                <>
                  <span className='sr-only'>Removing user...</span>
                  <Loader2 size={16} className='mx-3 animate-spin' />
                </>
              ) : (
                'Remove User'
              )}
            </Button>
          </CardFooter>
        </form>
      </Form>
    </Card>
  );
};

export const Route = createFileRoute(
  '/organizations/$orgId/products/$productId/users/'
)({
  loader: async ({ context, params }) => {
    await prefetchUseProductsServiceGetApiV1ProductsByProductId(
      context.queryClient,
      {
        productId: Number.parseInt(params.productId),
      }
    );
  },
  component: ManageUsers,
});
