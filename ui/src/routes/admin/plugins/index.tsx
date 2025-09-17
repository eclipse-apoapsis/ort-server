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

import { useMutation, useQuery } from '@tanstack/react-query';
import { createFileRoute, Link } from '@tanstack/react-router';

import { PluginDescriptor } from '@/api';
import {
  disablePluginMutation,
  enablePluginMutation,
  getInstalledPluginsOptions,
  getInstalledPluginsQueryKey,
} from '@/api/@tanstack/react-query.gen';
import { ToastError } from '@/components/toast-error.tsx';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card.tsx';
import { Switch } from '@/components/ui/switch.tsx';
import { ApiError } from '@/lib/api-error';
import { queryClient } from '@/lib/query-client.ts';
import { toast } from '@/lib/toast.ts';

type PluginListCardProps = {
  title: string;
  description: string;
  plugins: PluginDescriptor[];
};

const PluginListCard = ({
  title,
  description,
  plugins,
}: PluginListCardProps) => {
  const { mutateAsync: enablePlugin } = useMutation({
    ...enablePluginMutation(),
  });
  const { mutateAsync: disablePlugin } = useMutation({
    ...disablePluginMutation(),
  });

  const handleToggle = (plugin: PluginDescriptor) => {
    if (plugin.enabled) {
      disablePlugin(
        { path: { pluginType: plugin.type, pluginId: plugin.id } },
        {
          onSuccess: () => {
            toast.info('Disable Plugin', {
              description: `Plugin "${plugin.displayName}" disabled successfully.`,
            });
            queryClient.invalidateQueries({
              queryKey: getInstalledPluginsQueryKey(),
            });
          },
          onError(error: unknown) {
            const apiError = error as ApiError;
            toast.error(apiError.message, {
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
      enablePlugin(
        { path: { pluginType: plugin.type, pluginId: plugin.id } },
        {
          onSuccess: () => {
            toast.info('Enable Plugin', {
              description: `Plugin "${plugin.displayName}" enabled successfully.`,
            });
            queryClient.invalidateQueries({
              queryKey: getInstalledPluginsQueryKey(),
            });
          },
          onError(error: unknown) {
            const apiError = error as ApiError;
            toast.error(apiError.message, {
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

  return (
    <Card className='mb-4 h-fit'>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent>
        {plugins.map((plugin) => (
          <Card key={plugin.id} className='mb-2'>
            <CardHeader className='flex items-start justify-between'>
              <div>
                <CardTitle>{plugin.displayName}</CardTitle>
                <CardDescription>{plugin.description}</CardDescription>
              </div>
              <Switch
                className='data-[state=checked]:bg-green-500'
                checked={plugin.enabled}
                onCheckedChange={() => handleToggle(plugin)}
              />
            </CardHeader>
            <CardContent>
              <Link
                to={'/admin/plugins/$pluginType/$pluginId'}
                params={{
                  pluginType: plugin.type,
                  pluginId: plugin.id,
                }}
              >
                Manage Templates
              </Link>
            </CardContent>
          </Card>
        ))}
      </CardContent>
    </Card>
  );
};

const PluginsComponent = () => {
  const { data: plugins } = useQuery({
    ...getInstalledPluginsOptions(),
  });

  const advisors = plugins?.filter((plugin) => plugin.type === 'ADVISOR') || [];
  const packageConfigurationProviders =
    plugins?.filter(
      (plugin) => plugin.type === 'PACKAGE_CONFIGURATION_PROVIDER'
    ) || [];
  const packageCurationProviders =
    plugins?.filter((plugin) => plugin.type === 'PACKAGE_CURATION_PROVIDER') ||
    [];
  const packageManagers =
    plugins?.filter((plugin) => plugin.type === 'PACKAGE_MANAGER') || [];
  const reporters =
    plugins?.filter((plugin) => plugin.type === 'REPORTER') || [];
  const scanners = plugins?.filter((plugin) => plugin.type === 'SCANNER') || [];

  return (
    <div>
      <PluginListCard
        title='Advisors'
        description='Advisors integrate external vulnerability databases to get information about vulnerabilities in packages.'
        plugins={advisors}
      />
      <PluginListCard
        title='Package Configuration Providers'
        description='Providers for package configurations which are used to set path excludes and license finding curations for packages.'
        plugins={packageConfigurationProviders}
      />
      <PluginListCard
        title='Package Curation Providers'
        description='Providers for package curations which are used to correct metadata of packages.'
        plugins={packageCurationProviders}
      />
      <PluginListCard
        title='Package Managers'
        description='Package managers are used to detect packages and their dependencies.'
        plugins={packageManagers}
      />
      <PluginListCard
        title='Reporters'
        description='Reporters generate reports like SBOMs from the data collected in a run.'
        plugins={reporters}
      />
      <PluginListCard
        title='Scanners'
        description='Scanners scan packages for license and copyright information.'
        plugins={scanners}
      />
    </div>
  );
};

export const Route = createFileRoute('/admin/plugins/')({
  component: PluginsComponent,
});
