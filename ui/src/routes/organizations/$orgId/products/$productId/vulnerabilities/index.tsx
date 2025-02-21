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

import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';

import { prefetchUseProductsServiceGetApiV1ProductsByProductId } from '@/api/queries/prefetch';
import { useProductsServiceGetApiV1ProductsByProductIdSuspense } from '@/api/queries/suspense';
import { LoadingIndicator } from '@/components/loading-indicator';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import {
  packageIdentifierSearchParameterSchema,
  paginationSearchParameterSchema,
  sortingSearchParameterSchema,
  vulnerabilityRatingSearchParameterSchema,
} from '@/schemas';
import { ProductVulnerabilityTable } from './-components/product-vulnerability-table';

const ProductVulnerabilitiesComponent = () => {
  const params = Route.useParams();
  // These states are used to keep track of the total number of vulnerabilities
  // and the number of vulnerabilities after filtering.
  const [unfilteredRowCount, setUnfilteredRowCount] = useState(0);
  const [filteredRowCount, setFilteredRowCount] = useState(0);

  const { data: product } =
    useProductsServiceGetApiV1ProductsByProductIdSuspense({
      productId: Number.parseInt(params.productId),
    });
  const filtersInUse = unfilteredRowCount !== filteredRowCount;
  const matching = `, ${filteredRowCount} matching filters`;

  return (
    <Card className='h-fit'>
      <CardHeader>
        <CardTitle>
          Vulnerabilities in product: {product.name} ({unfilteredRowCount} in
          total{filtersInUse && matching})
        </CardTitle>
        <CardDescription>
          These are the vulnerabilities found currently from this product.
          Please note that the vulnerability status may change over time, as
          your dependencies change. Therefore, your product repositories should
          be scanned for vulnerabilities regularly.
        </CardDescription>
        <CardDescription>
          By clicking on "References" you can see more information about the
          vulnerability. The overall severity rating is calculated based on the
          highest severity rating found in the references.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <ProductVulnerabilityTable
          setUnfilteredRowCount={setUnfilteredRowCount}
          setFilteredRowCount={setFilteredRowCount}
        />
      </CardContent>
    </Card>
  );
};

export const Route = createFileRoute(
  '/organizations/$orgId/products/$productId/vulnerabilities/'
)({
  validateSearch: paginationSearchParameterSchema
    .merge(sortingSearchParameterSchema)
    .merge(packageIdentifierSearchParameterSchema)
    .merge(vulnerabilityRatingSearchParameterSchema),
  loader: async ({ context, params }) => {
    await prefetchUseProductsServiceGetApiV1ProductsByProductId(
      context.queryClient,
      {
        productId: Number.parseInt(params.productId),
      }
    );
  },
  component: ProductVulnerabilitiesComponent,
  pendingComponent: LoadingIndicator,
});
