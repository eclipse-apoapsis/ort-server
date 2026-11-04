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

import {
  useSuspenseQuery,
  type QueryKey,
  type UseSuspenseQueryOptions,
} from '@tanstack/react-query';
import {
  createFileRoute,
  Link,
  linkOptions,
  type LinkOptions,
} from '@tanstack/react-router';
import { AudioWaveform, List, ListVideo, type LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';

import type { PagingData } from '@/api';
import {
  getOrganizationsOptions,
  getRunsOptions,
} from '@/api/@tanstack/react-query.gen';
import { zOrtRunStatus } from '@/api/zod.gen';
import { QueryBoundary, QueryErrorFallback } from '@/components/query-boundary';
import { StatisticsCard } from '@/components/statistics-card';
import { Skeleton } from '@/components/ui/skeleton';

const organizationsCountOptions = getOrganizationsOptions({
  query: { limit: 1 },
});

const runsCountOptions = getRunsOptions({ query: { limit: 1 } });

const activeRunsCountOptions = getRunsOptions({
  query: { limit: 1, status: 'active' },
});

/**
 * Show the total count of a paged list query. The component suspends until the query has loaded.
 */
const TotalCount = <
  TData extends { pagination: PagingData },
  TError,
  TKey extends QueryKey,
>({
  options,
}: {
  options: UseSuspenseQueryOptions<TData, TError, TData, TKey>;
}) => {
  const { data } = useSuspenseQuery(options);

  return data.pagination.totalCount;
};

type AdminCount = {
  title: string;
  icon: LucideIcon;
  link: LinkOptions;
  value: ReactNode;
};

/**
 * Show one count of the overview. The count loads on its own: until its query has loaded, the card shows a
 * placeholder, and if the query fails, it shows a retry button instead of the link.
 */
const AdminCountCard = ({ title, icon: Icon, link, value }: AdminCount) => {
  const card = (cardValue: ReactNode, className?: string) => (
    <StatisticsCard
      title={title}
      icon={() => <Icon className='h-4 w-4' />}
      value={cardValue}
      className={className}
    />
  );

  return (
    <QueryBoundary
      fallback={card(<Skeleton className='h-8 w-12' />, 'h-full')}
      errorFallback={(props) =>
        card(<QueryErrorFallback {...props} />, 'h-full')
      }
      loadingLabel={`Loading ${title.toLowerCase()}...`}
    >
      <Link {...link}>{card(value, 'hover:bg-muted/50 h-full')}</Link>
    </QueryBoundary>
  );
};

const OverviewContent = () => {
  const counts: AdminCount[] = [
    {
      title: 'Organizations',
      icon: List,
      link: linkOptions({ to: '/organizations' }),
      value: <TotalCount options={organizationsCountOptions} />,
    },
    {
      title: 'Runs',
      icon: ListVideo,
      link: linkOptions({ to: '/admin/runs' }),
      value: <TotalCount options={runsCountOptions} />,
    },
    {
      title: 'Active runs',
      icon: AudioWaveform,
      link: linkOptions({
        to: '/admin/runs',
        search: { status: [zOrtRunStatus.enum.ACTIVE] },
      }),
      value: <TotalCount options={activeRunsCountOptions} />,
    },
  ];

  return (
    <div className='space-y-4'>
      <div className='grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4'>
        {counts.map((count) => (
          <AdminCountCard key={count.title} {...count} />
        ))}
      </div>
    </div>
  );
};

export const Route = createFileRoute('/admin/')({
  // Start all count queries without waiting for them, so that the page shows at once and every count appears as
  // soon as its own query has loaded.
  loader: ({ context: { queryClient } }) => {
    void queryClient.prefetchQuery(organizationsCountOptions);
    void queryClient.prefetchQuery(runsCountOptions);
    void queryClient.prefetchQuery(activeRunsCountOptions);
  },
  component: OverviewContent,
});
