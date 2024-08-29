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
  ArrowRightLeft,
  Cigarette,
  Eye,
  FileText,
  FolderTree,
} from 'lucide-react';

import { Sidebar } from '@/components/sidebar';

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
          title: 'Dependencies',
          icon: () => <FolderTree className='h-4 w-4' />,
        },
        {
          title: 'Vulnerabilities',
          icon: () => <Cigarette className='h-4 w-4' />,
        },
        {
          title: 'License Findings',
          icon: () => <FileText className='h-4 w-4' />,
        },
        {
          title: 'Rule Violations',
          icon: () => <ArrowRightLeft className='h-4 w-4' />,
        },
      ],
    },
    {
      label: 'Technical',
      items: [
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
    <div className='flex h-full w-full gap-2'>
      <Sidebar sections={sections} />
      <Outlet />
    </div>
  );
};
export const Route = createFileRoute(
  '/_layout/organizations/$orgId/products/$productId/repositories/$repoId/_layout'
)({
  component: Layout,
});
