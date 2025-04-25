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

import { createFileRoute, Outlet, redirect } from '@tanstack/react-router';
import {
  Blocks,
  Eye,
  KeyRound,
  ListVideo,
  Palette,
  PanelBottom,
  User,
} from 'lucide-react';

import { PageLayout } from '@/components/page-layout';
import { SidebarNavProps } from '@/components/sidebar';

const Layout = () => {
  const sections: SidebarNavProps['sections'] = [
    {
      items: [
        {
          title: 'Overview',
          to: '/admin',
          icon: () => <Eye className='h-4 w-4' />,
        },
      ],
    },
    {
      label: 'Status',
      items: [
        {
          title: 'Runs',
          to: '/admin/runs',
          icon: () => <ListVideo className='h-4 w-4' />,
        },
      ],
    },
    {
      label: 'User Management',
      items: [
        {
          title: 'Users',
          to: '/admin/users',
          icon: () => <User className='h-4 w-4' />,
        },
        {
          title: 'Authorization',
          to: '/admin/users/authorization',
          icon: () => <KeyRound className='h-4 w-4' />,
        },
      ],
    },
    {
      label: 'Plugin Management',
      items: [
        {
          title: 'Installed Plugins',
          to: '/admin/plugins',
          icon: () => <Blocks className='h-4 w-4' />,
        },
      ],
    },
    {
      label: 'Content Management',
      items: [
        {
          title: 'Colors',
          to: '/admin/colors',
          icon: () => <Palette className='h-4 w-4' />,
        },
        {
          title: 'Footer',
          to: '/admin/content-management/footer',
          icon: () => <PanelBottom className='h-4 w-4' />,
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

export const Route = createFileRoute('/admin')({
  component: Layout,
  beforeLoad: ({ context }) => {
    if (!context.auth.hasRole(['superuser'])) {
      throw redirect({
        to: '/403',
      });
    }
  },
});
