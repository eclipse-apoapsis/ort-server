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

import type { ComponentType } from 'react';

import type { VulnerabilityWithStats } from '@/api';
import { TooltipProvider } from '@/components/ui/tooltip';
import { renderInteractiveWithRouter } from './render-interactive';

// Renders the page of a run with the given subpath, such as 'issues', with the given search.
export const renderRunPage = (
  Component: ComponentType,
  subpage: string,
  search = ''
) =>
  renderInteractiveWithRouter(
    <TooltipProvider>
      <Component />
    </TooltipProvider>,
    {
      path: `/organizations/1/products/2/repositories/3/runs/4/${subpage}${search}`,
      routes: [
        {
          path: `/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/${subpage}/`,
        },
      ],
      withQueryClient: true,
    }
  );

// A vulnerability of the package with the given name, for the organization and product pages.
export const createVulnerabilityWithStats = (
  name: string
): VulnerabilityWithStats => ({
  identifier: {
    type: 'Maven',
    namespace: 'com.example',
    name,
    version: '1.0',
  },
  ortRunIds: [1],
  purl: `pkg:maven/com.example/${name}@1.0`,
  rating: 'HIGH',
  repositoriesCount: 2,
  vulnerability: {
    externalId: `CVE-${name}`,
    summary: `Summary of ${name}`,
    description: `Description of ${name}`,
    references: [],
  },
});
