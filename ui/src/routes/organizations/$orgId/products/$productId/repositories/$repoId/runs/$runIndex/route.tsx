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
  Boxes,
  Bug,
  Eye,
  FileText,
  FolderKanban,
  ListCheck,
  Logs,
  Scale,
  ShieldQuestion,
} from 'lucide-react';
import { Suspense } from 'react';

import { useRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRunsByOrtRunIndexKey } from '@/api/queries';
import { RepositoriesService } from '@/api/requests';
import { PageLayout } from '@/components/page-layout';
import { SidebarNavProps } from '@/components/sidebar';
import { RunDetailsBar } from './-components/run-details-bar';

const Layout = () => {
  const params = Route.useParams();

  const sections: SidebarNavProps['sections'] = [
    {
      items: [
        {
          title: 'Overview',
          to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex',
          params,
          icon: () => <Eye className='h-4 w-4' />,
        },
      ],
    },
    {
      label: 'Compliance',
      items: [
        {
          title: 'Vulnerabilities',
          to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/vulnerabilities',
          params,
          search: {
            sortBy: [{ id: 'rating', desc: true }],
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
            sortBy: [{ id: 'severity', desc: true }],
          },
          icon: () => <Scale className='h-4 w-4' />,
        },
        {
          title: 'SBOMs',
          to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/sbom',
          params,
          icon: () => <ListCheck className='h-4 w-4' />,
        },
      ],
    },
    {
      label: 'Components',
      items: [
        {
          title: 'Projects',
          to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/projects',
          params,
          icon: () => <FolderKanban className='h-4 w-4' />,
        },
        {
          title: 'Packages',
          to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/packages',
          params,
          icon: () => <Boxes className='h-4 w-4' />,
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
            sortBy: [{ id: 'severity', desc: true }],
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
          icon: () => <Logs className='h-4 w-4' />,
        },
        {
          title: 'Job Configurations',
          to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/config',
          params,
        },
      ],
    },
  ];

  return (
    <PageLayout sections={sections} topBar={<RunDetailsBar />}>
      <Suspense fallback={<div>Loading...</div>}>
        <Outlet />
      </Suspense>
    </PageLayout>
  );
};
export const Route = createFileRoute(
  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex'
)({
  loader: async ({ context, params }) => {
    const run = await context.queryClient.ensureQueryData({
      queryKey: [
        useRepositoriesServiceGetApiV1RepositoriesByRepositoryIdRunsByOrtRunIndexKey,
        params.repoId,
        params.runIndex,
      ],
      queryFn: () =>
        RepositoriesService.getApiV1RepositoriesByRepositoryIdRunsByOrtRunIndex(
          {
            repositoryId: Number.parseInt(params.repoId),
            ortRunIndex: Number.parseInt(params.runIndex),
          }
        ),
    });
    context.breadcrumbs.run = run.index.toString();
  },
  component: Layout,
});
