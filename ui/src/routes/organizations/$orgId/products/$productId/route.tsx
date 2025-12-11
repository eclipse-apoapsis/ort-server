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

import { getProductOptions } from '@/api/@tanstack/react-query.gen';
import { PageLayout } from '@/components/page-layout';
import { SidebarNavProps } from '@/components/sidebar';
import { fetchProductPermissions } from '@/lib/permissions.ts';

const Layout = () => {
  const { repoId, runIndex } = useParams({ strict: false });
  const productPermissions = Route.useRouteContext().permissions.product;

  const sections: SidebarNavProps['sections'] = [
    {
      items: [
        {
          title: 'Overview',
          to: '/organizations/$orgId/products/$productId',
          icon: () => <Eye className='h-4 w-4' />,
        },
      ],
    },
    {
      label: 'Compliance',
      items: [
        {
          title: 'Vulnerabilities',
          to: '/organizations/$orgId/products/$productId/vulnerabilities',
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
          to: '/organizations/$orgId/products/$productId/search-package',
          icon: () => <Search className='h-4 w-4' />,
        },
      ],
    },
    {
      label: 'Product',
      items: [
        {
          title: 'Secrets',
          to: '/organizations/$orgId/products/$productId/secrets',
          icon: () => <BookLock className='h-4 w-4' />,
          visible: productPermissions?.includes('WRITE_SECRETS'),
        },
        {
          title: 'Infrastructure Services',
          to: '/organizations/$orgId/products/$productId/infrastructure-services',
          icon: () => <ServerCog className='h-4 w-4' />,
          visible: productPermissions?.includes('WRITE'),
        },
        {
          title: 'Users',
          to: '/organizations/$orgId/products/$productId/users',
          icon: () => <User className='h-4 w-4' />,
          visible: productPermissions?.includes('MANAGE_GROUPS'),
        },
        {
          title: 'Settings',
          to: '/organizations/$orgId/products/$productId/settings',
          icon: () => <Settings className='h-4 w-4' />,
          visible: productPermissions?.includes('WRITE'),
        },
      ],
    },
  ];

  return (
    <>
      {!runIndex && !repoId ? (
        <PageLayout sections={sections}>
          <Outlet />
        </PageLayout>
      ) : (
        <Outlet />
      )}
    </>
  );
};

export const Route = createFileRoute(
  '/organizations/$orgId/products/$productId'
)({
  loader: async ({ context, params }) => {
    const productId = Number.parseInt(params.productId);
    try {
      const product = await context.queryClient.ensureQueryData({
        ...getProductOptions({
          path: { productId: productId },
        }),
      });

      const productPermissions = await fetchProductPermissions(
        context.queryClient,
        productId
      );

      context.breadcrumbs.product = product.name;
      context.permissions.product = productPermissions;
    } catch (error) {
      if (error instanceof AxiosError && error.status === 403) {
        context.breadcrumbs.product = undefined;
        context.permissions.product = undefined;
      }
    }
  },
  component: Layout,
});
