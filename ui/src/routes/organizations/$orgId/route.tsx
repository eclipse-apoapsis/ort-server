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

import { createFileRoute, Outlet, useParams } from '@tanstack/react-router';
import { AxiosError } from 'axios';
import {
  BookLock,
  Eye,
  Search,
  ServerCog,
  Settings,
  ShieldQuestion,
  User,
} from 'lucide-react';

import { getOrganizationOptions } from '@/api/@tanstack/react-query.gen';
import { PageLayout } from '@/components/page-layout';
import { SidebarNavProps } from '@/components/sidebar';
import { fetchOrganizationPermissions } from '@/lib/permissions.ts';

const Layout = () => {
  const { productId, repoId, runIndex } = useParams({ strict: false });
  const organizationPermissions =
    Route.useRouteContext().permissions.organization;

  const sections: SidebarNavProps['sections'] = [
    {
      items: [
        {
          title: 'Overview',
          to: '/organizations/$orgId',
          icon: () => <Eye className='h-4 w-4' />,
        },
      ],
    },
    {
      label: 'Compliance',
      items: [
        {
          title: 'Vulnerabilities',
          to: '/organizations/$orgId/vulnerabilities',
          search: {
            sortBy: [
              { id: 'rating', desc: true },
              { id: 'repositoriesCount', desc: true },
            ],
          },
          icon: () => <ShieldQuestion className='h-4 w-4' />,
        },
      ],
    },
    {
      label: 'Search',
      items: [
        {
          title: 'Packages',
          to: '/organizations/$orgId/search-package',
          icon: () => <Search className='h-4 w-4' />,
        },
        {
          title: 'Vulnerabilities',
          to: '/organizations/$orgId/search-vulnerability',
          icon: () => <Search className='h-4 w-4' />,
        },
      ],
    },
    {
      label: 'Organization',
      items: [
        {
          title: 'Secrets',
          to: '/organizations/$orgId/secrets',
          icon: () => <BookLock className='h-4 w-4' />,
          visible: organizationPermissions?.includes('WRITE_SECRETS'),
        },
        {
          title: 'Infrastructure Services',
          to: '/organizations/$orgId/infrastructure-services',
          icon: () => <ServerCog className='h-4 w-4' />,
          visible: organizationPermissions?.includes('WRITE'),
        },
        {
          title: 'Users',
          to: '/organizations/$orgId/users',
          icon: () => <User className='h-4 w-4' />,
          visible: organizationPermissions?.includes('MANAGE_GROUPS'),
        },
        {
          title: 'Settings',
          to: '/organizations/$orgId/settings',
          icon: () => <Settings className='h-4 w-4' />,
          visible: organizationPermissions?.includes('WRITE'),
        },
      ],
    },
  ];

  return (
    <>
      {!productId && !repoId && !runIndex ? (
        <PageLayout sections={sections}>
          <Outlet />
        </PageLayout>
      ) : (
        <Outlet />
      )}
    </>
  );
};

export const Route = createFileRoute('/organizations/$orgId')({
  loader: async ({ context, params }) => {
    const organizationId = Number.parseInt(params.orgId);

    try {
      const organization = await context.queryClient.ensureQueryData({
        ...getOrganizationOptions({
          path: { organizationId: organizationId },
        }),
      });

      const organizationPermissions = await fetchOrganizationPermissions(
        context.queryClient,
        organizationId
      );

      context.breadcrumbs.organization = organization.name;
      context.permissions.organization = organizationPermissions;
    } catch (error) {
      if (error instanceof AxiosError && error.status === 403) {
        context.breadcrumbs.organization = undefined;
        context.permissions.organization = undefined;
      }
    }
  },
  component: Layout,
});
