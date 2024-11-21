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
import { BookLock, Eye, User } from 'lucide-react';

import { PageLayout } from '@/components/page-layout';
import { SidebarNavProps } from '@/components/sidebar';
import { useUser } from '@/hooks/use-user';

const RepoLayout = () => {
  const { repoId } = Route.useParams();
  const user = useUser();

  const navItems: SidebarNavProps['sections'][number]['items'] = [
    {
      title: 'Overview',
      to: '/organizations/$orgId/products/$productId/repositories/$repoId',
      icon: () => <Eye className='h-4 w-4' />,
    },
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
      title: 'Users',
      to: '/organizations/$orgId/products/$productId/repositories/$repoId/users',
      icon: () => <User className='h-4 w-4' />,
      visible: user.hasRole(['superuser', `role_repository_${repoId}_admin`]),
    },
  ];

  return (
    <PageLayout sections={[{ items: navItems }]}>
      <Outlet />
    </PageLayout>
  );
};

export const Route = createFileRoute(
  '/_layout/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout'
)({
  component: RepoLayout,
});
