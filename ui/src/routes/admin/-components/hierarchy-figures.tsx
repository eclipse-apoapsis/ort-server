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

import { useQuery } from '@tanstack/react-query';
import { linkOptions } from '@tanstack/react-router';
import { ChevronRight } from 'lucide-react';

import { totalCount } from './overview-format';
import {
  organizationsCountOptions,
  productsCountOptions,
  repositoriesCountOptions,
} from './overview-queries';
import { OverviewFigure, QueryCount } from './overview-row';

const perParentFormat = new Intl.NumberFormat(undefined, {
  maximumFractionDigits: 1,
});

/**
 * How many children each parent holds on average, once both counts have loaded.
 */
const perParent = (
  count: number | undefined,
  parents: number | undefined,
  parentLabel: string
) =>
  count !== undefined && parents
    ? `${perParentFormat.format(count / parents)} per ${parentLabel}`
    : undefined;

const Step = () => (
  <ChevronRight
    className='text-muted-foreground/60 mt-1.5 hidden size-5 shrink-0 sm:block'
    aria-hidden
  />
);

/**
 * The numbers of organizations, products and repositories, each level leading to the next one.
 */
export const HierarchyFigures = () => {
  const organizations = useQuery(organizationsCountOptions()).data?.pagination
    .totalCount;
  const products = useQuery(productsCountOptions()).data?.pagination.totalCount;
  const repositories = useQuery(repositoriesCountOptions()).data?.pagination
    .totalCount;

  return (
    <div className='grid grid-cols-3 items-start gap-x-4 sm:flex sm:gap-x-6'>
      <OverviewFigure
        label='Organizations'
        link={linkOptions({ to: '/organizations' })}
        value={
          <QueryCount
            options={organizationsCountOptions()}
            count={totalCount}
          />
        }
      />
      <Step />
      <OverviewFigure
        label='Products'
        link={linkOptions({ to: '/admin/products' })}
        value={
          <QueryCount options={productsCountOptions()} count={totalCount} />
        }
        note={perParent(products, organizations, 'organization')}
      />
      <Step />
      <OverviewFigure
        label='Repositories'
        link={linkOptions({ to: '/admin/repositories' })}
        value={
          <QueryCount options={repositoriesCountOptions()} count={totalCount} />
        }
        note={perParent(repositories, products, 'product')}
      />
    </div>
  );
};
