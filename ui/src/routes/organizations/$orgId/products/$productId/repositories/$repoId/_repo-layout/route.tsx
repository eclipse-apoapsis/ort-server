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
import { BookLock, Eye, ServerCog, Settings, User } from 'lucide-react';

import { PageLayout } from '@/components/page-layout';
import { SidebarNavProps } from '@/components/sidebar';
import { useUser } from '@/hooks/use-user';

const RepoLayout = () => {
  const { repoId } = Route.useParams();
  const user = useUser();

  const sections: SidebarNavProps['sections'] = [
    {
      items: [
        {
          title: 'Overview',
          to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs',
          icon: () => <Eye className='h-4 w-4' />,
        },
      ],
    },
    {
      label: 'Repository',
      items: [
        {
          title: 'Secrets',
          to: '/organizations/$orgId/products/$productId/repositories/$repoId/secrets',
          icon: () => <BookLock className='h-4 w-4' />,
          visible: user.hasRole([
            'superuser',
            `permission_repository_${repoId}_write_secrets`,
          ]),
        },
        {
          title: 'Infrastructure Services',
          to: '/organizations/$orgId/products/$productId/repositories/$repoId/infrastructure-services',
          icon: () => <ServerCog className='h-4 w-4' />,
          visible: user.hasRole([
            'superuser',
            `permission_repository_${repoId}_admin`,
          ]),
        },
        {
          title: 'Users',
          to: '/organizations/$orgId/products/$productId/repositories/$repoId/users',
          icon: () => <User className='h-4 w-4' />,
          visible: user.hasRole([
            'superuser',
            `role_repository_${repoId}_admin`,
          ]),
        },
        {
          title: 'Settings',
          to: '/organizations/$orgId/products/$productId/repositories/$repoId/settings',
          icon: () => <Settings className='h-4 w-4' />,
          visible: user.hasRole([
            'superuser',
            `role_repository_${repoId}_admin`,
          ]),
        },
      ],
    },
  ];

  return (
    <PageLayout sections={sections}>
      <Outlet />
    </PageLayout>
  );
};

export const Route = createFileRoute(
  '/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout'
)({
  component: RepoLayout,
});
