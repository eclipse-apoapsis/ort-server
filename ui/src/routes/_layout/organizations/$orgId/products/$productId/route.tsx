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
import { BookLock, Eye } from 'lucide-react';
import { Suspense } from 'react';

import { useProductsServiceGetProductByIdKey } from '@/api/queries';
import { ProductsService } from '@/api/requests';
import { Sidebar } from '@/components/sidebar';
import { useUser } from '@/hooks/use-user';

const Layout = () => {
  const { productId, repoId, runIndex } = useParams({ strict: false });
  const user = useUser();

  const navItems = [
    {
      title: 'Overview',
      to: '/organizations/$orgId/products/$productId',
      icon: () => <Eye className='h-4 w-4' />,
    },
    {
      title: 'Secrets',
      to: '/organizations/$orgId//products/$productId/secrets',
      icon: () => <BookLock className='h-4 w-4' />,
      visible: user.hasRole([
        'superuser',
        `permission_product_${productId}_write_secrets`,
      ]),
    },
  ];

  return (
    <>
      {!runIndex && !repoId && <Sidebar sections={[{ items: navItems }]} />}
      <Suspense fallback={<div>Loading...</div>}>
        <Outlet />
      </Suspense>
    </>
  );
};

export const Route = createFileRoute(
  '/_layout/organizations/$orgId/products/$productId'
)({
  loader: async ({ context, params }) => {
    const product = await context.queryClient.ensureQueryData({
      queryKey: [useProductsServiceGetProductByIdKey, params.productId],
      queryFn: () =>
        ProductsService.getProductById({
          productId: Number.parseInt(params.productId),
        }),
    });
    context.breadcrumbs.product = product.name;
  },
  component: Layout,
});
