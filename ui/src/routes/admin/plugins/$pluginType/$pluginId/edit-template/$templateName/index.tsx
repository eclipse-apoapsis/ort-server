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
import { useMutation, useSuspenseQuery } from '@tanstack/react-query';
import {
  createFileRoute,
  useLoaderData,
  useNavigate,
} from '@tanstack/react-router';
import { Loader2 } from 'lucide-react';
import { FieldValues, Resolver, useForm, UseFormReturn } from 'react-hook-form';
import { z } from 'zod';

import { PluginOption } from '@/api';
import {
  getPluginTemplateOptions,
  getPluginTemplateQueryKey,
  getPluginTemplatesQueryKey,
  updatePluginTemplateOptionsMutation,
} from '@/api/@tanstack/react-query.gen';
import { ToastError } from '@/components/toast-error.tsx';
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
  FormDescription,
  FormItem,
  FormLabel,
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
} from '../../-helpers.ts';
import { Route as LayoutRoute } from '../../../../route.tsx';

function buildFormSchema(options: Array<PluginOption>) {
  return z.object(buildPluginOptionsFormShape(options));
}

type FormValues = Record<string, unknown>;

const EditTemplate = () => {
  const navigate = useNavigate();
  const { plugins } = useLoaderData({ from: LayoutRoute.id });
  const params = Route.useParams();

  const plugin = plugins.find(
    (p) => p.type === params.pluginType && p.id === params.pluginId
  );

  const { data: template } = useSuspenseQuery({
    ...getPluginTemplateOptions({
      path: {
        pluginType: params.pluginType,
        pluginId: params.pluginId,
        templateName: params.templateName,
      },
    }),
  });

  const formSchema = plugin?.options
    ? buildFormSchema(plugin.options)
    : z.object({});

  const form = useForm<FormValues>({
    resolver: zodResolver(formSchema) as unknown as Resolver<FormValues>,
    values:
      plugin?.options?.reduce((acc, option) => {
        const templateOption = template.options.find(
          (o) => o.option === option.name
        );
        acc[option.name] = templateOption
          ? parseStoredPluginOptionValue(templateOption.value, option.type)
          : option.type === 'BOOLEAN'
            ? false
            : option.type === 'ENUM_LIST'
              ? parseStoredPluginOptionValue(option.defaultValue, option.type)
              : (option.defaultValue ?? '');
        acc[`${option.name}_isFinal`] = templateOption?.isFinal ?? false;
        acc[`${option.name}_isNotSet`] = !templateOption;
        return acc;
      }, {} as FormValues) ?? {},
  });

  const { mutateAsync: updateTemplate, isPending: isUpdateTemplatePending } =
    useMutation({
      ...updatePluginTemplateOptionsMutation(),
      onSuccess() {
        toast.info('Edit Template', {
          description: `Template updated successfully.`,
        });
        queryClient.invalidateQueries({
          queryKey: getPluginTemplateQueryKey({
            path: {
              pluginType: params.pluginType,
              pluginId: params.pluginId,
              templateName: params.templateName,
            },
          }),
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

  async function onSubmit(formValues: FormValues) {
    const requestBody = buildPluginTemplateRequestBody(
      plugin?.options,
      formValues
    );

    await updateTemplate({
      path: {
        pluginType: params.pluginType,
        pluginId: params.pluginId,
        templateName: params.templateName,
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
        <CardTitle>Edit Template</CardTitle>
        <CardDescription>
          Edit the plugin template for the {params.pluginId}{' '}
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
            <FormItem>
              <FormLabel>Template Name</FormLabel>
              <Input value={params.templateName} disabled />
              <FormDescription>The name of the template.</FormDescription>
            </FormItem>
            {plugin?.options && (
              <PluginOptionFormFields
                options={plugin.options}
                form={form as unknown as UseFormReturn<FieldValues>}
              />
            )}
          </CardContent>
          <CardFooter className='mt-6 gap-4'>
            <Button type='submit' disabled={isUpdateTemplatePending}>
              {isUpdateTemplatePending ? (
                <>
                  <span className='sr-only'>Updating Template...</span>
                  <Loader2 size={16} className='mx-3 animate-spin' />
                </>
              ) : (
                'Update Template'
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
              disabled={isUpdateTemplatePending}
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
  '/admin/plugins/$pluginType/$pluginId/edit-template/$templateName/'
)({
  loader: async ({ context: { queryClient }, params }) => {
    await queryClient.ensureQueryData({
      ...getPluginTemplateOptions({
        path: {
          pluginType: params.pluginType,
          pluginId: params.pluginId,
          templateName: params.templateName,
        },
      }),
    });
  },
  component: EditTemplate,
});
