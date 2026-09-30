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

// @vitest-environment jsdom

import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { PackageCuration as Curation } from '@/api';
import { PackageCuration } from '@/components/package-curation';
import { formatTimestamp } from '@/lib/utils';

const createCuration = (publishedAt?: string): Curation => ({
  providerName: 'ClearlyDefined',
  data: { declaredLicenseMapping: {}, labels: {}, publishedAt },
});

describe('package curation', () => {
  it('shows the publication date of a curation', () => {
    render(
      <PackageCuration curation={createCuration('2024-05-06T07:08:09Z')} />
    );

    expect(screen.getByText('Published:')).toBeInTheDocument();
    expect(
      screen.getByText(formatTimestamp('2024-05-06T07:08:09Z'))
    ).toBeInTheDocument();
  });

  it('shows no publication date for a curation without one', () => {
    render(<PackageCuration curation={createCuration()} />);

    expect(screen.queryByText('Published:')).not.toBeInTheDocument();
  });
});
