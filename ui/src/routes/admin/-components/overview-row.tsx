/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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
import { Link, type LinkOptions } from '@tanstack/react-router';
import { type LucideIcon } from 'lucide-react';
import { useId, type ReactNode } from 'react';

import { QueryBoundary, QueryErrorFallback } from '@/components/query-boundary';
import { Skeleton } from '@/components/ui/skeleton';
import { formatCount } from './overview-format';

/**
 * Show the number a query returns. The component suspends until the query has loaded.
 */
export const QueryCount = <TData, TError, TKey extends QueryKey>({
  options,
  count,
}: {
  options: UseSuspenseQueryOptions<TData, TError, TData, TKey>;
  count: (data: TData) => number;
}) => {
  const { data } = useSuspenseQuery(options);

  return formatCount(count(data));
};

type OverviewFigureProps = {
  label: string;
  link: LinkOptions;
  value: ReactNode;
  // A line under the label, such as how this number relates to another one.
  note?: ReactNode;
};

/**
 * One number of an area, linking to the page that lists what it counts. The number loads on its
 * own: until then it shows a placeholder, and if its request fails, a retry button instead of the
 * link.
 */
export const OverviewFigure = ({
  label,
  link,
  value,
  note,
}: OverviewFigureProps) => {
  const figure = (number: ReactNode) => (
    <>
      <span className='text-3xl leading-none font-semibold tracking-tight tabular-nums'>
        {number}
      </span>
      <span className='text-muted-foreground text-sm'>{label}</span>
    </>
  );

  return (
    <div className='flex flex-col gap-1'>
      <QueryBoundary
        fallback={
          <div className='flex flex-col gap-1.5'>
            {figure(<Skeleton className='h-7.5 w-14' />)}
          </div>
        }
        errorFallback={(props) => (
          <div className='flex flex-col items-start gap-1.5'>
            {figure(<QueryErrorFallback {...props} />)}
          </div>
        )}
        loadingLabel={`Loading the number of ${label.toLowerCase()}...`}
      >
        <Link
          {...link}
          className='group/figure flex flex-col gap-1.5 rounded-sm outline-offset-4'
        >
          {figure(
            <span className='decoration-2 underline-offset-4 group-hover/figure:underline'>
              {value}
            </span>
          )}
        </Link>
      </QueryBoundary>
      {note && <span className='text-muted-foreground text-xs'>{note}</span>}
    </div>
  );
};

type OverviewRowProps = {
  title: string;
  description: string;
  icon: LucideIcon;
  figures?: ReactNode;
};

/**
 * One area of the administration: what it is and its numbers.
 */
export const OverviewRow = ({
  title,
  description,
  icon: Icon,
  figures,
}: OverviewRowProps) => {
  const headingId = useId();

  return (
    <section
      aria-labelledby={headingId}
      className='grid gap-x-10 gap-y-5 px-4 py-6 sm:px-6 lg:grid-cols-[15rem_minmax(0,1fr)]'
    >
      <div className='flex flex-col gap-1.5'>
        <h2
          id={headingId}
          className='flex items-center gap-2 text-base font-semibold'
        >
          <Icon className='text-muted-foreground size-4' aria-hidden />
          {title}
        </h2>
        <p className='text-muted-foreground text-sm text-pretty'>
          {description}
        </p>
      </div>
      <div className='flex flex-wrap items-start gap-x-12 gap-y-5'>
        {figures}
      </div>
    </section>
  );
};
