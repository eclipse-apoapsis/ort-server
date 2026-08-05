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

import { useMutation, useQueries, useQuery } from '@tanstack/react-query';
import { createFileRoute, Link, useLoaderData } from '@tanstack/react-router';
import { Pencil } from 'lucide-react';
import { useState } from 'react';

import { PluginDescriptor, PluginTemplate } from '@/api';
import {
  addTemplateToOrganizationMutation,
  deletePluginTemplateMutation,
  disableGlobalPluginTemplateMutation,
  enableGlobalPluginTemplateMutation,
  getOrganizationOptions,
  getOrganizationsInfiniteOptions,
  getPluginTemplatesOptions,
  getPluginTemplatesQueryKey,
  removeTemplateFromOrganizationMutation,
} from '@/api/@tanstack/react-query.gen';
import { DeleteDialog } from '@/components/delete-dialog.tsx';
import { DeleteIconButton } from '@/components/delete-icon-button.tsx';
import { LoadingIndicator } from '@/components/loading-indicator.tsx';
import { Badge } from '@/components/ui/badge.tsx';
import { Button } from '@/components/ui/button.tsx';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card.tsx';
import MultipleSelector, {
  Option,
} from '@/components/ui/multiple-selector.tsx';
import { Switch } from '@/components/ui/switch.tsx';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { useDebounce } from '@/hooks/use-debounce';
import { useInfiniteList } from '@/hooks/use-infinite-list';
import { ApiError } from '@/lib/api-error';
import { DROPDOWN_PAGE_SIZE } from '@/lib/constants.ts';
import { queryClient, routePrefetchStaleTime } from '@/lib/query-client.ts';
import { toSearchFilter } from '@/lib/regex';
import { toast, toastError } from '@/lib/toast';
import { getPluginTypeLabel } from '@/lib/types';
import { Route as LayoutRoute } from '@/routes/admin/plugins/route.tsx';

type PluginTemplateCardProps = {
  plugin: PluginDescriptor;
  template: PluginTemplate;
};

const PluginTemplateCard = ({ plugin, template }: PluginTemplateCardProps) => {
  // Nothing is loaded before the user has touched the field, so a page with many templates on it
  // costs no request for the organizations of any of them.
  const [isSelectorUsed, setIsSelectorUsed] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');

  const filter = toSearchFilter(useDebounce(searchTerm, 300));

  const organizations = useInfiniteList(
    getOrganizationsInfiniteOptions({
      query: { limit: DROPDOWN_PAGE_SIZE, filter: filter },
    }),
    { enabled: isSelectorUsed }
  );

  const organizationOptions: Option[] = organizations.items.map(
    (organization) => ({
      label: organization.name,
      value: organization.id.toString(),
    })
  );

  // The organizations this template is assigned to are not necessarily part of the page that is
  // shown, so their names are read one by one. The queries are shared by all the cards.
  const assignedIds = template.organizationIds ?? [];

  const assignedOrganizations = useQueries({
    queries: assignedIds.map((organizationId) =>
      getOrganizationOptions({ path: { organizationId: organizationId } })
    ),
  });

  const assignedOptions: Option[] = assignedIds.map(
    (organizationId, index) => ({
      label:
        assignedOrganizations[index]?.data?.name ?? organizationId.toString(),
      value: organizationId.toString(),
    })
  );

  const { mutateAsync: enableGlobal } = useMutation({
    ...enableGlobalPluginTemplateMutation(),
  });
  const { mutateAsync: disableGlobal } = useMutation({
    ...disableGlobalPluginTemplateMutation(),
  });
  const { mutateAsync: deleteTemplate } = useMutation({
    ...deletePluginTemplateMutation(),
  });

  const toggleIsGlobal = () => {
    if (template.isGlobal) {
      disableGlobal(
        {
          path: {
            pluginType: template.pluginType,
            pluginId: template.pluginId,
            templateName: template.name,
          },
        },
        {
          onSuccess: () => {
            toast.info('Template disabled globally', {
              description: `Template "${template.name}" is no longer global.`,
            });
            queryClient.invalidateQueries({
              queryKey: getPluginTemplatesQueryKey({
                path: {
                  pluginType: template.pluginType,
                  pluginId: template.pluginId,
                },
              }),
            });
          },
          onError: (error: unknown) => {
            const apiError = error as ApiError;
            toastError('Failed to disable template globally', apiError);
          },
        }
      );
    } else {
      enableGlobal(
        {
          path: {
            pluginType: template.pluginType,
            pluginId: template.pluginId,
            templateName: template.name,
          },
        },
        {
          onSuccess: () => {
            toast.info('Template enabled globally', {
              description: `Template "${template.name}" is now global.`,
            });
            queryClient.invalidateQueries({
              queryKey: getPluginTemplatesQueryKey({
                path: {
                  pluginType: template.pluginType,
                  pluginId: template.pluginId,
                },
              }),
            });
          },
          onError: (error: unknown) => {
            const apiError = error as ApiError;
            toastError('Failed to enable template globally', apiError);
          },
        }
      );
    }
  };

  const onDelete = () => {
    deleteTemplate(
      {
        path: {
          pluginType: template.pluginType,
          pluginId: template.pluginId,
          templateName: template.name,
        },
      },
      {
        onSuccess: () => {
          toast.info('Template deleted', {
            description: `Template "${template.name}" deleted successfully.`,
          });
          queryClient.invalidateQueries({
            queryKey: getPluginTemplatesQueryKey({
              path: {
                pluginType: template.pluginType,
                pluginId: template.pluginId,
              },
            }),
          });
        },
        onError: (error: unknown) => {
          const apiError = error as ApiError;
          toastError('Failed to delete template', apiError);
        },
      }
    );
  };

  const { mutateAsync: addOrganization } = useMutation({
    ...addTemplateToOrganizationMutation(),
    onSuccess: () => {
      toast.info('Organization added to template', {
        description: `Organization added to template "${template.name}".`,
      });
      queryClient.invalidateQueries({
        queryKey: getPluginTemplatesQueryKey({
          path: {
            pluginType: template.pluginType,
            pluginId: template.pluginId,
          },
        }),
      });
    },
    onError: (error) => {
      const apiError = error as ApiError;
      toastError('Failed to add organization to template', apiError);
    },
  });

  const { mutateAsync: removeOrganization } = useMutation({
    ...removeTemplateFromOrganizationMutation(),
    onSuccess: () => {
      toast.info('Organization removed from template', {
        description: `Organization removed from template "${template.name}".`,
      });
      queryClient.invalidateQueries({
        queryKey: getPluginTemplatesQueryKey({
          path: {
            pluginType: template.pluginType,
            pluginId: template.pluginId,
          },
        }),
      });
    },
    onError: (error) => {
      const apiError = error as ApiError;
      toastError('Failed to remove organization from template', apiError);
    },
  });

  return (
    <Card className='mb-2'>
      <CardHeader className='flex flex-row items-center justify-between'>
        <CardTitle>{template.name}</CardTitle>
        <div className='flex items-center gap-1'>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button variant='outline' size='sm' className='h-8 px-2' asChild>
                <Link
                  to='/admin/plugins/$pluginType/$pluginId/edit-template/$templateName'
                  params={{
                    pluginType: template.pluginType,
                    pluginId: template.pluginId,
                    templateName: template.name,
                  }}
                >
                  <span className='sr-only'>Edit Template</span>
                  <Pencil size={16} />
                </Link>
              </Button>
            </TooltipTrigger>
            <TooltipContent>Edit Template</TooltipContent>
          </Tooltip>
          <DeleteDialog
            thingName={'template'}
            uiComponent={<DeleteIconButton />}
            onDelete={onDelete}
            tooltip='Delete Template'
          />
        </div>
      </CardHeader>
      <CardContent>
        <div className='flex items-center gap-2'>
          <span className='text-sm'>Global Template</span>
          <Switch
            className='data-[state=checked]:bg-green-500'
            checked={template.isGlobal}
            onCheckedChange={() => toggleIsGlobal()}
          />
        </div>

        <p className='text-muted-foreground mb-1 text-sm'>
          {template.isGlobal
            ? 'This template is global, it is active for all organizations that do not have another template assigned.'
            : 'This template is not global, it is only active for organizations that it is assigned to.'}
        </p>

        <div className='my-4 border-t' />

        <p className='text-muted-foreground mb-1 text-sm'>
          {template.organizationIds && template.organizationIds.length > 0
            ? 'This template is assigned to the following organizations:'
            : 'This template is not assigned to any organizations.'}
        </p>
        <MultipleSelector
          value={assignedOptions}
          options={organizationOptions}
          loading={organizations.isPending}
          hasMore={organizations.hasNextPage}
          isLoadingMore={organizations.isFetchingNextPage}
          onLoadMore={organizations.fetchNextPage}
          // The organizations are searched on the server, so the list must not be filtered again
          // by what has been typed.
          commandProps={{ shouldFilter: false }}
          inputProps={{
            onValueChange: setSearchTerm,
            onFocus: () => setIsSelectorUsed(true),
          }}
          emptyIndicator={
            <p className='text-center text-lg leading-10 text-gray-600 dark:text-gray-400'>
              No results found.
            </p>
          }
          loadingIndicator={
            <p className='text-center text-lg leading-10 text-gray-600 dark:text-gray-400'>
              Loading...
            </p>
          }
          placeholder='Assign organizations...'
          badgeClassName='bg-amber-200 text-black'
          onChange={(newSelected) => {
            const oldIds = template.organizationIds ?? [];
            const newIds = newSelected.map((o) => Number(o.value));
            for (const opt of newSelected) {
              if (!oldIds.includes(Number(opt.value))) {
                addOrganization({
                  path: {
                    pluginType: template.pluginType,
                    pluginId: template.pluginId,
                    templateName: template.name,
                  },
                  query: { organizationId: opt.value },
                });
              }
            }
            for (const id of oldIds) {
              if (!newIds.includes(id)) {
                removeOrganization({
                  path: {
                    pluginType: template.pluginType,
                    pluginId: template.pluginId,
                    templateName: template.name,
                  },
                  query: { organizationId: id.toString() },
                });
              }
            }
          }}
        />

        <div className='my-4 border-t' />

        {plugin.options?.map((pluginOption) => {
          const templateOption = template.options.find(
            (opt) => opt.option === pluginOption.name
          );
          return (
            <div
              key={pluginOption.name}
              className='mb-2 flex items-center gap-2'
            >
              <span className='text-sm font-semibold'>
                {pluginOption.name}:
              </span>
              {templateOption ? (
                <>
                  <span className='text-sm'>{templateOption.value}</span>
                  <Badge className='bg-blue-200 text-black'>
                    {pluginOption.type}
                  </Badge>
                  {templateOption.isFinal && (
                    <Badge className='bg-green-200 text-black'>Final</Badge>
                  )}
                </>
              ) : (
                <>
                  <Badge className='bg-gray-200 text-black'>Not set</Badge>
                  <Badge className='bg-blue-200 text-black'>
                    {pluginOption.type}
                  </Badge>
                </>
              )}
            </div>
          );
        })}
      </CardContent>
    </Card>
  );
};

const PluginTemplatesComponent = () => {
  const params = Route.useParams();
  const { plugins } = useLoaderData({ from: LayoutRoute.id });

  const pluginType = params.pluginType;
  const pluginId = params.pluginId;

  const plugin = plugins.find(
    (p) => p.type === pluginType && p.id === pluginId
  );

  const {
    data: pluginTemplates,
    error: pluginTemplatesError,
    isPending: pluginTemplatesIsPending,
    isError: pluginTemplatesIsError,
  } = useQuery({
    ...getPluginTemplatesOptions({
      path: {
        pluginType: pluginType,
        pluginId: pluginId,
      },
    }),
    staleTime: routePrefetchStaleTime,
  });

  if (!plugin) {
    toast.error('Plugin not found', {
      description: `No plugin found with type "${pluginType}" and id "${pluginId}".`,
      duration: Infinity,
      cancel: {
        label: 'Dismiss',
        onClick: () => {},
      },
    });
    return;
  }

  if (pluginTemplatesIsPending) {
    return <LoadingIndicator />;
  }

  if (pluginTemplatesIsError) {
    toastError('Failed to load plugin templates', pluginTemplatesError);
    return;
  }

  return (
    <Card className='mb-4 h-fit'>
      <CardHeader>
        <CardTitle>
          {plugin.displayName} Templates ({pluginTemplates.length})
        </CardTitle>
        <CardDescription>
          Manage plugin templates for the {plugin.displayName}{' '}
          {getPluginTypeLabel(pluginType)} plugin.
        </CardDescription>
        <div className='py-2'>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button asChild size='sm' className='ml-auto gap-1'>
                <Link
                  to={'/admin/plugins/$pluginType/$pluginId/create-template'}
                  params={{
                    pluginType: pluginType,
                    pluginId: pluginId,
                  }}
                >
                  Create Template
                </Link>
              </Button>
            </TooltipTrigger>
            <TooltipContent>
              Create a new plugin configuration template.
            </TooltipContent>
          </Tooltip>
        </div>
      </CardHeader>
      <CardContent>
        {pluginTemplates.length > 0 ? (
          pluginTemplates.map((template) => (
            <PluginTemplateCard
              key={template.name}
              plugin={plugin}
              template={template}
            />
          ))
        ) : (
          <p>No templates found for this plugin.</p>
        )}
      </CardContent>
    </Card>
  );
};

export const Route = createFileRoute('/admin/plugins/$pluginType/$pluginId/')({
  loader: async ({ context: { queryClient }, params }) => {
    await queryClient.prefetchQuery({
      ...getPluginTemplatesOptions({
        path: {
          pluginType: params.pluginType,
          pluginId: params.pluginId,
        },
      }),
      staleTime: routePrefetchStaleTime,
    });
  },
  component: PluginTemplatesComponent,
  pendingComponent: LoadingIndicator,
});
