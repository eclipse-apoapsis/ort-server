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

import { createFileRoute, redirect } from '@tanstack/react-router';

import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';

const ProductSecrets = () => {
  return (
    <Card className='mx-auto w-full max-w-4xl'>
      <CardHeader>
        <CardTitle>Secrets</CardTitle>
        <CardDescription>Manage secrets for this product.</CardDescription>
      </CardHeader>
      <CardContent>
        <div>Content here.</div>
      </CardContent>
    </Card>
  );
};

export const Route = createFileRoute(
  '/_layout/organizations/$orgId/products/$productId/secrets'
)({
  component: ProductSecrets,
  beforeLoad: ({ context, params }) => {
    if (
      !context.auth.hasRole([
        'superuser',
        `permission_product_${params.productId}_write_secrets`,
      ])
    ) {
      throw redirect({
        to: '/403',
      });
    }
  },
});
