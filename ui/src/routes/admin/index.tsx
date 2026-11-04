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

import { createFileRoute, linkOptions } from '@tanstack/react-router';
import { Blocks, ListVideo, Network, User } from 'lucide-react';

import { DefaultPluginsNote } from './-components/default-plugins-note';
import { HierarchyFigures } from './-components/hierarchy-figures';
import { InstalledPluginsNote } from './-components/installed-plugins-note';
import { defaultPluginsCount, totalCount } from './-components/overview-format';
import {
  defaultPluginsOptions,
  installedPluginsOptions,
  prefetchOverview,
  runsCountOptions,
  runStatusCountOptions,
  usersCountOptions,
} from './-components/overview-queries';
import {
  OverviewFigure,
  OverviewRow,
  QueryCount,
} from './-components/overview-row';
import { RunStatusSummary } from './-components/run-status-summary';
import { WaitingRunsNote } from './-components/waiting-runs-note';

const OverviewContent = () => (
  <div className='flex flex-col gap-4'>
    <div className='flex flex-col gap-1'>
      <h1 className='text-2xl font-semibold tracking-tight'>Overview</h1>
      <p className='text-muted-foreground text-sm'>
        The runs, hierarchy, users and plugins of this server.
      </p>
    </div>
    <div className='bg-card text-card-foreground divide-y rounded-xl border shadow-sm'>
      <OverviewRow
        title='Runs'
        description='See how many runs are active and waiting, and catch the ones that fail.'
        icon={ListVideo}
        figures={
          <>
            <OverviewFigure
              label='Active'
              link={linkOptions({
                to: '/admin/runs',
                search: { status: ['ACTIVE'] },
              })}
              note={<WaitingRunsNote />}
              value={
                <QueryCount
                  options={runStatusCountOptions('ACTIVE')}
                  count={totalCount}
                />
              }
            />
            <OverviewFigure
              label='Runs in total'
              link={linkOptions({ to: '/admin/runs' })}
              value={
                <QueryCount options={runsCountOptions} count={totalCount} />
              }
            />
            <RunStatusSummary />
          </>
        }
      />
      <OverviewRow
        title='Hierarchy'
        description='See how many organizations, products and repositories the server holds, and open their lists.'
        icon={Network}
        figures={<HierarchyFigures />}
      />
      <OverviewRow
        title='Users'
        description='Manage who can sign in, and their roles in organizations, products and repositories.'
        icon={User}
        figures={
          <OverviewFigure
            label='Users'
            link={linkOptions({ to: '/admin/users' })}
            value={
              <QueryCount options={usersCountOptions} count={totalCount} />
            }
          />
        }
      />
      <OverviewRow
        title='Plugins'
        description='Choose the plugins the workers run, and the defaults for new runs.'
        icon={Blocks}
        figures={
          <>
            <OverviewFigure
              label='Installed plugins'
              link={linkOptions({ to: '/admin/plugins' })}
              note={<InstalledPluginsNote />}
              value={
                <QueryCount
                  options={installedPluginsOptions}
                  count={(plugins) => plugins.length}
                />
              }
            />
            <OverviewFigure
              label='Default plugins'
              link={linkOptions({ to: '/admin/plugins/defaults' })}
              note={<DefaultPluginsNote />}
              value={
                <QueryCount
                  options={defaultPluginsOptions}
                  count={defaultPluginsCount}
                />
              }
            />
          </>
        }
      />
    </div>
  </div>
);

export const Route = createFileRoute('/admin/')({
  // Start all queries without waiting for them, so that the page shows at once and every number
  // appears as soon as its own query has loaded.
  loader: ({ context: { queryClient } }) => prefetchOverview(queryClient),
  component: OverviewContent,
});
