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

import { createFileRoute, Link } from '@tanstack/react-router';
import { AudioWaveform, List, ListVideo, Loader2 } from 'lucide-react';

import {
  useOrganizationsServiceGetOrganizations,
  useRunsServiceGetOrtRuns,
} from '@/api/queries';
import { StatisticsCard } from '@/components/statistics-card';
import { ToastError } from '@/components/toast-error';
import { toast } from '@/lib/toast';
import { runStatusSchema } from '@/schemas';

const OverviewContent = () => {
  const {
    data: orgs,
    isLoading: orgsIsLoading,
    error: orgIsError,
  } = useOrganizationsServiceGetOrganizations({
    limit: 1,
  });

  const {
    data: runs,
    isLoading: runsIsLoading,
    error: runsIsError,
  } = useRunsServiceGetOrtRuns({
    limit: 1,
  });

  const {
    data: activeRuns,
    isLoading: activeRunsIsLoading,
    error: activeRunsIsError,
  } = useRunsServiceGetOrtRuns({
    limit: 1,
    status: 'active',
  });

  if (orgIsError || runsIsError || activeRunsIsError) {
    toast.error('Unable to load data', {
      description: (
        <ToastError error={orgIsError || runsIsError || activeRunsIsError} />
      ),
      duration: Infinity,
      cancel: {
        label: 'Dismiss',
        onClick: () => {},
      },
    });
  }

  return (
    <div className='space-y-4'>
      <div className='grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4'>
        <Link to='/'>
          <StatisticsCard
            title='Organizations'
            icon={() => <List className='h-4 w-4' />}
            value={
              orgsIsLoading ? (
                <>
                  <span className='sr-only'>Loading...</span>
                  <Loader2 size={16} className='animate-spin' />
                </>
              ) : orgIsError ? (
                <span className='text-sm text-red-500'>
                  Error fetching data
                </span>
              ) : (
                orgs?.pagination.totalCount
              )
            }
            className='h-full hover:bg-muted/50'
          />
        </Link>
        <Link to='/admin/runs'>
          <StatisticsCard
            title='ORT runs'
            icon={() => <ListVideo className='h-4 w-4' />}
            value={
              runsIsLoading ? (
                <>
                  <span className='sr-only'>Loading...</span>
                  <Loader2 size={16} className='animate-spin' />
                </>
              ) : runsIsError ? (
                <span className='text-sm text-red-500'>
                  Error fetching data
                </span>
              ) : (
                runs?.pagination.totalCount
              )
            }
            className='h-full'
          />
        </Link>
        <Link
          to='/admin/runs'
          search={{ status: [runStatusSchema.Values.ACTIVE] }}
        >
          <StatisticsCard
            title='Active runs'
            icon={() => <AudioWaveform className='h-4 w-4' />}
            value={
              activeRunsIsLoading ? (
                <>
                  <span className='sr-only'>Loading...</span>
                  <Loader2 size={16} className='animate-spin' />
                </>
              ) : activeRunsIsError ? (
                <span className='text-sm text-red-500'>
                  Error fetching data
                </span>
              ) : (
                activeRuns?.pagination.totalCount
              )
            }
            className='h-full'
          />
        </Link>
      </div>
    </div>
  );
};

export const Route = createFileRoute('/_layout/admin/')({
  component: OverviewContent,
});
