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

import { createFileRoute, Outlet } from '@tanstack/react-router';
import {
  BookLock,
  Boxes,
  Bug,
  Eye,
  FileText,
  History,
  ListTree,
  Scale,
  ShieldQuestion,
  User,
} from 'lucide-react';
import { Suspense } from 'react';

import { useRepositoriesServiceGetOrtRunByIndexKey } from '@/api/queries';
import { RepositoriesService } from '@/api/requests';
import { PageLayout } from '@/components/page-layout';
import { SidebarNavProps } from '@/components/sidebar';
import { useUser } from '@/hooks/use-user';

const Layout = () => {
  const params = Route.useParams();
  const user = useUser();

  const sections: SidebarNavProps['sections'] = [
    {
      items: [
        {
          title: 'Overview',
          to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex',
          params,
          icon: () => <Eye className='h-4 w-4' />,
        },
        {
          title: 'All Runs',
          to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs',
          params,
          icon: () => <History className='h-4 w-4' />,
        },
      ],
    },
    {
      label: 'Compliance',
      items: [
        {
          title: 'Packages',
          to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/packages',
          params,
          icon: () => <Boxes className='h-4 w-4' />,
        },
        {
          title: 'Dependencies',
          to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/dependencies',
          params,
          icon: () => <ListTree className='h-4 w-4' />,
        },
        {
          title: 'Vulnerabilities',
          to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/vulnerabilities',
          params,
          search: {
            sortBy: 'rating.desc',
          },
          icon: () => <ShieldQuestion className='h-4 w-4' />,
        },
        {
          title: 'License Findings',
          to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/license-findings',
          params,
          icon: () => <FileText className='h-4 w-4' />,
        },
        {
          title: 'Rule Violations',
          to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/rule-violations',
          params,
          search: {
            sortBy: 'severity.desc',
          },
          icon: () => <Scale className='h-4 w-4' />,
        },
      ],
    },
    {
      label: 'Technical',
      items: [
        {
          title: 'Issues',
          to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/issues',
          params,
          search: {
            sortBy: 'severity.desc',
          },
          icon: () => <Bug className='h-4 w-4' />,
        },
        {
          title: 'Reports',
          to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/reports',
          params,
        },
        {
          title: 'Logs',
          to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/logs',
          params,
        },
        {
          title: 'Job Configurations',
          to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/config',
          params,
        },
      ],
    },
    {
      label: 'Repository',
      visible: user.hasRole([
        'superuser',
        `role_repository_${params.repoId}_admin`,
        `permission_repository_${params.repoId}_write_secrets`,
      ]),
      items: [
        {
          title: 'Secrets',
          to: '/organizations/$orgId/products/$productId/repositories/$repoId/secrets',
          params,
          icon: () => <BookLock className='h-4 w-4' />,
          visible: user.hasRole([
            'superuser',
            `permission_repository_${params.repoId}_write_secrets`,
          ]),
        },
        {
          title: 'Users',
          to: '/organizations/$orgId/products/$productId/repositories/$repoId/users',
          params,
          icon: () => <User className='h-4 w-4' />,
          visible: user.hasRole([
            'superuser',
            `role_repository_${params.repoId}_admin`,
          ]),
        },
      ],
    },
  ];

  return (
    <PageLayout sections={sections}>
      <Suspense fallback={<div>Loading...</div>}>
        <Outlet />
      </Suspense>
    </PageLayout>
  );
};
export const Route = createFileRoute(
  '/_layout/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex'
)({
  loader: async ({ context, params }) => {
    const run = await context.queryClient.ensureQueryData({
      queryKey: [
        useRepositoriesServiceGetOrtRunByIndexKey,
        params.repoId,
        params.runIndex,
      ],
      queryFn: () =>
        RepositoriesService.getOrtRunByIndex({
          repositoryId: Number.parseInt(params.repoId),
          ortRunIndex: Number.parseInt(params.runIndex),
        }),
    });
    context.breadcrumbs.run = run.index.toString();
  },
  component: Layout,
});
