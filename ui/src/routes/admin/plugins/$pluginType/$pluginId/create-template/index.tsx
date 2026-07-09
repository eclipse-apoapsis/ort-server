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
import { useMutation } from '@tanstack/react-query';
import {
  createFileRoute,
  useLoaderData,
  useNavigate,
} from '@tanstack/react-router';
import { Loader2 } from 'lucide-react';
import { FieldValues, useForm, UseFormReturn } from 'react-hook-form';
import { z } from 'zod';

import { PluginOption } from '@/api';
import {
  createPluginTemplateMutation,
  getPluginTemplatesQueryKey,
} from '@/api/@tanstack/react-query.gen';
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
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { ApiError } from '@/lib/api-error';
import { queryClient } from '@/lib/query-client';
import { toast, toastError } from '@/lib/toast';
import { getPluginTypeLabel } from '@/lib/types';
import { PluginOptionFormFields } from '@/routes/admin/plugins/$pluginType/$pluginId/-components/plugin-option-form-fields.tsx';
import {
  buildPluginOptionsFormShape,
  buildPluginTemplateRequestBody,
  parseStoredPluginOptionValue,
} from '../-helpers.ts';
import { Route as LayoutRoute } from '../../../route.tsx';

const templateName = 'Template Name';

function buildFormSchema(options: Array<PluginOption>) {
  const shape = buildPluginOptionsFormShape(options);
  shape[templateName] = z.string().min(1);
  return z.object(shape);
}

type FormValues = {
  [templateName]: string;
  [key: string]: unknown;
};

const CreateTemplate = () => {
  const navigate = useNavigate();
  const { plugins } = useLoaderData({ from: LayoutRoute.id });
  const params = Route.useParams();

  const plugin = plugins.find(
    (p) => p.type === params.pluginType && p.id === params.pluginId
  );

  const formSchema = plugin?.options
    ? buildFormSchema(plugin.options)
    : z.object({ [templateName]: z.string().min(1) });

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      [templateName]: '',
      ...(plugin?.options?.reduce(
        (acc, option) => {
          acc[option.name] =
            option.type === 'BOOLEAN'
              ? false
              : option.type === 'ENUM_LIST'
                ? parseStoredPluginOptionValue(option.defaultValue, option.type)
                : (option.defaultValue ?? '');
          acc[`${option.name}_isFinal`] = false;
          acc[`${option.name}_isNotSet`] = true;
          return acc;
        },
        {} as Record<string, unknown>
      ) || {}),
    },
  });

  const { mutateAsync: createTemplate, isPending: isCreateTemplatePending } =
    useMutation({
      ...createPluginTemplateMutation(),
      onSuccess() {
        toast.info('Create Template', {
          description: `Template created successfully.`,
        });
        queryClient.invalidateQueries({
          queryKey: getPluginTemplatesQueryKey({
            path: {
              pluginType: params.pluginType,
              pluginId: params.pluginId,
            },
          }),
        });
        navigate({
          to: '/admin/plugins/$pluginType/$pluginId',
          params: {
            pluginType: params.pluginType,
            pluginId: params.pluginId,
          },
        });
      },
      onError(error) {
        const apiError = error as ApiError;
        toastError(error.message, apiError);
      },
    });

  async function onSubmit(values: z.infer<typeof formSchema>) {
    const formValues = values as FormValues;
    const requestBody = buildPluginTemplateRequestBody(
      plugin?.options,
      formValues
    );

    await createTemplate({
      path: {
        pluginType: params.pluginType,
        pluginId: params.pluginId,
        templateName: formValues[templateName],
      },
      body: requestBody,
    });
  }

  if (plugin === undefined) {
    toast.error('Unable to find plugin', {
      description: <ToastError error='Plugin not found.' />,
      duration: Infinity,
      cancel: {
        label: 'Dismiss',
        onClick: () => {},
      },
    });
    return;
  }

  return (
    <Card className='col-span-2 w-full'>
      <CardHeader>
        <CardTitle>Create Template</CardTitle>
        <CardDescription>
          Create a new plugin template for the {plugin.displayName}{' '}
          {getPluginTypeLabel(params.pluginType)} plugin.
          <br />
          Options that are set to final can not be overwritten by the user.
          <br />
          Options that are set to undefined will not be set in the template.
        </CardDescription>
      </CardHeader>
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <CardContent className='space-y-4'>
            <FormField
              key={templateName}
              control={form.control}
              name={templateName}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Template Name</FormLabel>
                  <FormControl>
                    <Input
                      {...field}
                      value={typeof field.value === 'string' ? field.value : ''}
                    />
                  </FormControl>
                  <FormDescription>
                    The name of the template to create.
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
            {plugin.options && (
              <PluginOptionFormFields
                options={plugin.options}
                form={form as unknown as UseFormReturn<FieldValues>}
              />
            )}
          </CardContent>
          <CardFooter className='mt-6 gap-4'>
            <Button type='submit' disabled={isCreateTemplatePending}>
              {isCreateTemplatePending ? (
                <>
                  <span className='sr-only'>Creating Template...</span>
                  <Loader2 size={16} className='mx-3 animate-spin' />
                </>
              ) : (
                'Create Template'
              )}
            </Button>
            <Button
              type='button'
              variant='outline'
              onClick={() =>
                navigate({
                  to: '/admin/plugins/$pluginType/$pluginId',
                  params: {
                    pluginType: params.pluginType,
                    pluginId: params.pluginId,
                  },
                })
              }
              disabled={isCreateTemplatePending}
            >
              Cancel
            </Button>
          </CardFooter>
        </form>
      </Form>
    </Card>
  );
};

export const Route = createFileRoute(
  '/admin/plugins/$pluginType/$pluginId/create-template/'
)({
  component: CreateTemplate,
});
