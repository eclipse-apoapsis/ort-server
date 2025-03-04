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
import {
  BookLock,
  Eye,
  ServerCog,
  Settings,
  ShieldQuestion,
  User,
} from 'lucide-react';

import { useOrganizationsServiceGetApiV1OrganizationsByOrganizationIdKey } from '@/api/queries';
import { OrganizationsService } from '@/api/requests';
import { PageLayout } from '@/components/page-layout';
import { SidebarNavProps } from '@/components/sidebar';
import { useUser } from '@/hooks/use-user';

const Layout = () => {
  const { orgId, productId, repoId, runIndex } = useParams({ strict: false });
  const user = useUser();

  const navItems: SidebarNavProps['sections'][number]['items'] = [
    {
      title: 'Overview',
      to: '/organizations/$orgId',
      icon: () => <Eye className='h-4 w-4' />,
    },
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
    {
      title: 'Secrets',
      to: '/organizations/$orgId/secrets',
      icon: () => <BookLock className='h-4 w-4' />,
      visible: user.hasRole([
        'superuser',
        `permission_organization_${orgId}_write_secrets`,
      ]),
    },
    {
      title: 'Infrastructure Services',
      to: '/organizations/$orgId/infrastructure-services',
      icon: () => <ServerCog className='h-4 w-4' />,
      visible: user.hasRole(['superuser', `role_organization_${orgId}_admin`]),
    },
    {
      title: 'Users',
      to: '/organizations/$orgId/users',
      icon: () => <User className='h-4 w-4' />,
      visible: user.hasRole(['superuser', `role_organization_${orgId}_admin`]),
    },
    {
      title: 'Settings',
      to: '/organizations/$orgId/settings',
      icon: () => <Settings className='h-4 w-4' />,
      visible: user.hasRole(['superuser', `role_organization_${orgId}_admin`]),
    },
  ];

  return (
    <>
      {!productId && !repoId && !runIndex ? (
        <PageLayout sections={[{ items: navItems }]}>
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
    const organization = await context.queryClient.ensureQueryData({
      queryKey: [
        useOrganizationsServiceGetApiV1OrganizationsByOrganizationIdKey,
        params.orgId,
      ],
      queryFn: () =>
        OrganizationsService.getApiV1OrganizationsByOrganizationId({
          organizationId: Number.parseInt(params.orgId),
        }),
    });
    context.breadcrumbs.organization = organization.name;
  },
  component: Layout,
});
