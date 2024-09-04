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
  Eye,
  FileText,
  ListTree,
  Scale,
  ShieldQuestion,
} from 'lucide-react';

import { PageLayout } from '@/components/page-layout';

const Layout = () => {
  const sections = [
    {
      label: 'Compliance',
      items: [
        {
          title: 'Overview',
          to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex',
          icon: () => <Eye className='h-4 w-4' />,
        },
        {
          title: 'Packages',
          to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/packages',
          icon: () => <Boxes className='h-4 w-4' />,
        },
        {
          title: 'Dependencies',
          to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/dependencies',
          icon: () => <ListTree className='h-4 w-4' />,
        },
        {
          title: 'Vulnerabilities',
          to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/vulnerabilities',
          icon: () => <ShieldQuestion className='h-4 w-4' />,
        },
        {
          title: 'License Findings',
          to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/license-findings',
          icon: () => <FileText className='h-4 w-4' />,
        },
        {
          title: 'Rule Violations',
          to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/rule-violations',
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
        },
        {
          title: 'Reports',
          to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/reports',
        },
        {
          title: 'Logs',
          to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/logs',
        },
        {
          title: 'Job Configurations',
          to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/config',
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
  '/_layout/organizations/$orgId/products/$productId/repositories/$repoId/_layout'
)({
  component: Layout,
});
