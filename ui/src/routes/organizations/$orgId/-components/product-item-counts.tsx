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

import { getProductRunStatisticsOptions } from '@/api/@tanstack/react-query.gen';
import { ItemCounts } from '@/components/item-counts';
import { config } from '@/config';

type ProductItemCountsProps = {
  productId: number;
};

export const ProductItemCounts = ({ productId }: ProductItemCountsProps) => {
  const statistics = useQuery({
    ...getProductRunStatisticsOptions({
      path: { productId },
    }),
    refetchInterval: config.pollInterval,
  });

  return (
    <ItemCounts
      statistics={statistics.data}
      compact
      tooltip={(label, count) => `${count} ${label} in total`}
    />
  );
};
