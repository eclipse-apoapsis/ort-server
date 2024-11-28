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

import { createFileRoute, notFound, redirect } from '@tanstack/react-router';

import { ApiError, RunsService } from '@/api/requests';

export const Route = createFileRoute('/runs/$runId/')({
  beforeLoad: async ({ params }) => {
    let organizationId, productId, repositoryId, index;
    try {
      const ortRun = await RunsService.getOrtRunById({
        runId: Number.parseInt(params.runId),
      });
      organizationId = ortRun.organizationId;
      productId = ortRun.productId;
      repositoryId = ortRun.repositoryId;
      index = ortRun.index;
    } catch (error) {
      if (error instanceof ApiError) {
        throw notFound();
      }
    }
    if (organizationId && productId && repositoryId && index) {
      throw redirect({
        to: `/organizations/${organizationId}/products/${productId}/repositories/${repositoryId}/runs/${index}`,
      });
    }
  },
  notFoundComponent: () => {
    return <div>ORT run not found!</div>;
  },
});
