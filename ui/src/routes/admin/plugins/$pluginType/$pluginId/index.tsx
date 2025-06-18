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

import { createFileRoute, Link, useLoaderData } from '@tanstack/react-router';
import { ChevronsUpDownIcon } from 'lucide-react';

import {
  useOrganizationsServiceGetApiV1Organizations,
  usePluginsServiceDeleteApiV1AdminPluginsByPluginTypeByPluginIdTemplatesByTemplateName,
  usePluginsServiceGetApiV1AdminPluginsByPluginTypeByPluginIdTemplates,
  usePluginsServiceGetApiV1AdminPluginsByPluginTypeByPluginIdTemplatesKey,
  usePluginsServicePostApiV1AdminPluginsByPluginTypeByPluginIdTemplatesByTemplateNameAddToOrganization,
  usePluginsServicePostApiV1AdminPluginsByPluginTypeByPluginIdTemplatesByTemplateNameDisableGlobal,
  usePluginsServicePostApiV1AdminPluginsByPluginTypeByPluginIdTemplatesByTemplateNameEnableGlobal,
  usePluginsServicePostApiV1AdminPluginsByPluginTypeByPluginIdTemplatesByTemplateNameRemoveFromOrganization,
} from '@/api/queries';
import { prefetchUsePluginsServiceGetApiV1AdminPluginsByPluginTypeByPluginIdTemplates } from '@/api/queries/prefetch.ts';
import { ApiError, PluginDescriptor, PluginTemplate } from '@/api/requests';
import { DeleteDialog } from '@/components/delete-dialog.tsx';
import { DeleteIconButton } from '@/components/delete-icon-button.tsx';
import { LoadingIndicator } from '@/components/loading-indicator.tsx';
import { ToastError } from '@/components/toast-error.tsx';
import { Badge } from '@/components/ui/badge.tsx';
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
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/components/ui/command.tsx';
import { Option } from '@/components/ui/multiple-selector.tsx';
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover.tsx';
import { Switch } from '@/components/ui/switch.tsx';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { ALL_ITEMS } from '@/lib/constants.ts';
import { queryClient } from '@/lib/query-client.ts';
import { toast } from '@/lib/toast';
import { Route as LayoutRoute } from '@/routes/admin/plugins/route.tsx';

type PluginTemplateCardProps = {
  plugin: PluginDescriptor;
  template: PluginTemplate;
  organizationOptions: Option[];
};

const PluginTemplateCard = ({
  plugin,
  template,
  organizationOptions,
}: PluginTemplateCardProps) => {
  const enableGlobal =
    usePluginsServicePostApiV1AdminPluginsByPluginTypeByPluginIdTemplatesByTemplateNameEnableGlobal();
  const disableGlobal =
    usePluginsServicePostApiV1AdminPluginsByPluginTypeByPluginIdTemplatesByTemplateNameDisableGlobal();
  const deleteTemplate =
    usePluginsServiceDeleteApiV1AdminPluginsByPluginTypeByPluginIdTemplatesByTemplateName();

  const toggleIsGlobal = () => {
    if (template.isGlobal) {
      disableGlobal.mutate(
        {
          pluginType: template.pluginType,
          pluginId: template.pluginId,
          templateName: template.name,
        },
        {
          onSuccess: () => {
            toast.info('Template disabled globally', {
              description: `Template "${template.name}" is no longer global.`,
            });
            queryClient.invalidateQueries({
              queryKey: [
                usePluginsServiceGetApiV1AdminPluginsByPluginTypeByPluginIdTemplatesKey,
                {
                  pluginType: template.pluginType,
                  pluginId: template.pluginId,
                },
              ],
            });
          },
          onError: (error: unknown) => {
            const apiError = error as ApiError;
            toast.error('Failed to disable template globally', {
              description: <ToastError error={apiError} />,
              duration: Infinity,
              cancel: {
                label: 'Dismiss',
                onClick: () => {},
              },
            });
          },
        }
      );
    } else {
      enableGlobal.mutate(
        {
          pluginType: template.pluginType,
          pluginId: template.pluginId,
          templateName: template.name,
        },
        {
          onSuccess: () => {
            toast.info('Template enabled globally', {
              description: `Template "${template.name}" is now global.`,
            });
            queryClient.invalidateQueries({
              queryKey: [
                usePluginsServiceGetApiV1AdminPluginsByPluginTypeByPluginIdTemplatesKey,
                {
                  pluginType: template.pluginType,
                  pluginId: template.pluginId,
                },
              ],
            });
          },
          onError: (error: unknown) => {
            const apiError = error as ApiError;
            toast.error('Failed to enable template globally', {
              description: <ToastError error={apiError} />,
              duration: Infinity,
              cancel: {
                label: 'Dismiss',
                onClick: () => {},
              },
            });
          },
        }
      );
    }
  };

  const onDelete = () => {
    deleteTemplate.mutate(
      {
        pluginType: template.pluginType,
        pluginId: template.pluginId,
        templateName: template.name,
      },
      {
        onSuccess: () => {
          toast.info('Template deleted', {
            description: `Template "${template.name}" deleted successfully.`,
          });
          queryClient.invalidateQueries({
            queryKey: [
              usePluginsServiceGetApiV1AdminPluginsByPluginTypeByPluginIdTemplatesKey,
              {
                pluginType: template.pluginType,
                pluginId: template.pluginId,
              },
            ],
          });
        },
        onError: (error: unknown) => {
          const apiError = error as ApiError;
          toast.error('Failed to delete template', {
            description: <ToastError error={apiError} />,
            duration: Infinity,
            cancel: {
              label: 'Dismiss',
              onClick: () => {},
            },
          });
        },
      }
    );
  };

  const { mutateAsync: addOrganization } =
    usePluginsServicePostApiV1AdminPluginsByPluginTypeByPluginIdTemplatesByTemplateNameAddToOrganization(
      {
        onSuccess: () => {
          toast.info('Organization added to template', {
            description: `Organization added to template "${template.name}".`,
          });
          queryClient.invalidateQueries({
            queryKey: [
              usePluginsServiceGetApiV1AdminPluginsByPluginTypeByPluginIdTemplatesKey,
              {
                pluginType: template.pluginType,
                pluginId: template.pluginId,
              },
            ],
          });
        },
        onError: (error: unknown) => {
          const apiError = error as ApiError;
          toast.error('Failed to add organization to template', {
            description: <ToastError error={apiError} />,
            duration: Infinity,
            cancel: {
              label: 'Dismiss',
              onClick: () => {},
            },
          });
        },
      }
    );

  const { mutateAsync: removeOrganization } =
    usePluginsServicePostApiV1AdminPluginsByPluginTypeByPluginIdTemplatesByTemplateNameRemoveFromOrganization(
      {
        onSuccess: () => {
          toast.info('Organization removed from template', {
            description: `Organization removed from template "${template.name}".`,
          });
          queryClient.invalidateQueries({
            queryKey: [
              usePluginsServiceGetApiV1AdminPluginsByPluginTypeByPluginIdTemplatesKey,
              {
                pluginType: template.pluginType,
                pluginId: template.pluginId,
              },
            ],
          });
        },
        onError: (error: unknown) => {
          const apiError = error as ApiError;
          toast.error('Failed to remove organization from template', {
            description: <ToastError error={apiError} />,
            duration: Infinity,
            cancel: {
              label: 'Dismiss',
              onClick: () => {},
            },
          });
        },
      }
    );

  return (
    <Card key={template.name} className='mb-2'>
      <CardHeader className='flex items-start justify-between'>
        <CardTitle>{template.name}</CardTitle>
      </CardHeader>
      <CardContent>
        <div>
          Global Template
          <Switch
            className='ml-2 data-[state:checked]:bg-green-500'
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

        <div>
          {template.organizationIds && template.organizationIds.length > 0 && (
            <div className='mb-2 flex flex-wrap gap-2'>
              {template.organizationIds.map((orgId) => {
                const org = organizationOptions.find(
                  (o) => o.value === orgId.toString()
                );
                return (
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <Badge
                        asChild
                        key={orgId}
                        className='bg-amber-200 text-black'
                      >
                        <Button
                          variant='ghost'
                          onClick={() => {
                            removeOrganization({
                              pluginType: template.pluginType,
                              pluginId: template.pluginId,
                              templateName: template.name,
                              organizationId: orgId.toString(),
                            });
                          }}
                        >
                          {org ? org.label : orgId}
                        </Button>
                      </Badge>
                    </TooltipTrigger>
                    <TooltipContent>Remove Organization</TooltipContent>
                  </Tooltip>
                );
              })}
            </div>
          )}
        </div>

        <Popover>
          <PopoverTrigger asChild>
            <Button variant='ghost' role='combobox' className='px-1'>
              Add to Organization
              <ChevronsUpDownIcon className='ml-2 h-4 w-4 shrink-0 opacity-50' />
            </Button>
          </PopoverTrigger>
          <PopoverContent>
            <Command>
              <CommandInput placeholder='Search organization...' />
              <CommandList>
                <CommandEmpty>No organization found.</CommandEmpty>
                <CommandGroup>
                  {organizationOptions
                    .filter(
                      (option) =>
                        !template.organizationIds?.includes(
                          Number(option.value)
                        )
                    )
                    .map((option) => (
                      <CommandItem
                        key={option.value}
                        value={option.label}
                        onSelect={() => {
                          addOrganization({
                            pluginType: template.pluginType,
                            pluginId: template.pluginId,
                            templateName: template.name,
                            organizationId: option.value,
                          });
                        }}
                      >
                        {option.label}
                      </CommandItem>
                    ))}
                </CommandGroup>
              </CommandList>
            </Command>
          </PopoverContent>
        </Popover>

        <div className='my-4 border-t' />

        {plugin.options?.map((pluginOption) => {
          const templateOption = template.options.find(
            (opt) => opt.option === pluginOption.name
          );
          return (
            <div key={pluginOption.name} className='mb-2'>
              <strong>{pluginOption.name}:</strong>{' '}
              {templateOption ? (
                <>
                  {templateOption.value}
                  {templateOption.isFinal && (
                    <Badge className='ml-2 bg-green-200 text-black'>
                      Final
                    </Badge>
                  )}
                </>
              ) : (
                <Badge className='ml-2 bg-gray-200 text-black'>Not set</Badge>
              )}
              <Badge className='ml-2 bg-blue-200 text-black'>
                {pluginOption.type}
              </Badge>
            </div>
          );
        })}
      </CardContent>
      <CardFooter>
        Delete Template
        <span className='ml-2'>
          <DeleteDialog
            thingName={'template'}
            uiComponent={<DeleteIconButton />}
            onDelete={onDelete}
            tooltip='Delete Template'
          />
        </span>
      </CardFooter>
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
    data: organizations,
    isPending: orgIsPending,
    isError: orgIsError,
    error: orgError,
  } = useOrganizationsServiceGetApiV1Organizations({
    limit: ALL_ITEMS,
  });

  const {
    data: pluginTemplates,
    error: pluginTemplatesError,
    isPending: pluginTemplatesIsPending,
    isError: pluginTemplatesIsError,
  } = usePluginsServiceGetApiV1AdminPluginsByPluginTypeByPluginIdTemplates({
    pluginType: pluginType,
    pluginId: pluginId,
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
    toast.error('Failed to load plugin templates', {
      description: <ToastError error={pluginTemplatesError} />,
      duration: Infinity,
      cancel: {
        label: 'Dismiss',
        onClick: () => {},
      },
    });
    return;
  }

  if (orgIsPending) {
    return <LoadingIndicator />;
  }

  if (orgIsError) {
    toast.error('Unable to load data', {
      description: <ToastError error={orgError} />,
      duration: Infinity,
      cancel: {
        label: 'Dismiss',
        onClick: () => {},
      },
    });
    return;
  }

  const organizationOptions: Option[] = organizations.data.map((o) => ({
    label: o.name,
    value: o.id.toString(),
  }));

  return (
    <Card className='mb-4 h-fit'>
      <CardHeader>
        <CardTitle>
          {pluginId} Templates ({pluginTemplates.length})
        </CardTitle>
        <CardDescription>
          Manage plugin templates for the {pluginId} {pluginType} plugin.
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
              plugin={plugin}
              template={template}
              organizationOptions={organizationOptions}
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
  loader: async ({ context, params }) => {
    await prefetchUsePluginsServiceGetApiV1AdminPluginsByPluginTypeByPluginIdTemplates(
      context.queryClient,
      {
        pluginType: params.pluginType,
        pluginId: params.pluginId,
      }
    );
  },
  component: PluginTemplatesComponent,
  pendingComponent: LoadingIndicator,
});
