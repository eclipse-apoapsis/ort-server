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

import { Sidebar } from '@/components/sidebar';

const Layout = () => {
  const navItems = [
    {
      title: 'Overview',
      to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex',
    },
    {
      title: 'Dependencies',
    },
    {
      title: 'Vulnerabilities',
    },
    {
      title: 'License Findings',
    },
    {
      title: 'Rule Violations',
    },
    {
      title: 'Reports',
      to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/reports',
    },
    {
      title: 'Logs',
      to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/logs',
    },
  ];

  return (
    <div className='flex h-[calc(100vh-4rem-2rem)] w-full gap-2 md:h-[calc(100vh-4rem-4rem)]'>
      <Sidebar sections={[{ items: navItems }]} />
      <Outlet />
    </div>
  );
};
export const Route = createFileRoute(
  '/_layout/organizations/$orgId/products/$productId/repositories/$repoId/_layout'
)({
  component: Layout,
});
