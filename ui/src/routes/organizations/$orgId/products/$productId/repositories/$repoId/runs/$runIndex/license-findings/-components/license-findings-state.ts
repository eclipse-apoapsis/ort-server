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

import { ExpandedState } from '@tanstack/react-table';

import { PackageIdType } from '@/schemas';

export const getMarkerExpandedState = (marker?: string): ExpandedState =>
  marker ? { [marker]: true } : {};

export const getDetectedLicenseQueryFilter = (
  marked: string | undefined,
  license: string | undefined
) => (marked ? { license: marked } : { license });

export const getPackageIdentifierQueryFilter = (
  packageMarked: string | undefined,
  packageIdType: PackageIdType,
  packageId: string | undefined
) => {
  if (packageMarked) {
    return {
      identifier: packageMarked,
      identifierMatchType: 'exact',
    };
  }

  return packageIdType === 'PURL'
    ? { purl: packageId }
    : { identifier: packageId };
};

export const clearDetectedLicenseMarkers = <T extends object>(search: T) => ({
  ...search,
  marked: undefined,
  packageMarked: undefined,
});

export const clearPackageMarker = <T extends object>(search: T) => ({
  ...search,
  packageMarked: undefined,
});
