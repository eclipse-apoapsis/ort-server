/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import { Link } from '@tanstack/react-router';

import type { Identifier } from '@/api';
import { BreakableString } from '@/components/breakable-string';
import { CopyToClipboard } from '@/components/copy-to-clipboard';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import {
  getExactPackageLinkSearch,
  identifierToString,
} from '@/helpers/identifier-conversion';
import { packageIdTypeSchema, type PackageIdType } from '@/schemas';

type RunPackageLinkProps = {
  identifier: Identifier | null | undefined;
  purl: string | null | undefined;
  packageIdType: PackageIdType;
  params: {
    orgId: string;
    productId: string;
    repoId: string;
    runIndex: string;
  };
  // Issues and rule violations without a PURL belong to a project.
  linkProjectWhenPurlIsNull?: boolean;
  copyClassName?: string;
};

// Links a finding of a run to its package in the packages table, or to its
// project in the projects table, and offers to copy the shown identifier.
// The link finds the package or project by an exact match of its ORT ID,
// whichever identifier is shown.
export const RunPackageLink = ({
  identifier,
  purl,
  packageIdType,
  params,
  linkProjectWhenPurlIsNull = false,
  copyClassName,
}: RunPackageLinkProps) => {
  const shownPurl =
    packageIdType === packageIdTypeSchema.enum.PURL && purl ? purl : undefined;
  const id = shownPurl ?? identifierToString(identifier);
  const idType = shownPurl
    ? packageIdTypeSchema.enum.PURL
    : packageIdTypeSchema.enum.ORT_ID;
  // The API leaves out a PURL that is null, so it arrives as undefined.
  const linksProject = linkProjectWhenPurlIsNull && purl == null;

  return (
    <>
      <Tooltip>
        <TooltipTrigger asChild>
          {linksProject ? (
            <Link
              className='text-left font-semibold text-blue-400 hover:underline'
              to='/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/projects'
              params={params}
              search={{
                projectId: id,
                projectIdMatchType: 'exact',
                marked: id,
              }}
            >
              <BreakableString text={id} />
            </Link>
          ) : (
            <Link
              className='text-left font-semibold text-blue-400 hover:underline'
              to='/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/packages'
              params={params}
              search={
                identifier
                  ? getExactPackageLinkSearch(identifier)
                  : { pkgId: id, pkgIdType: idType }
              }
            >
              <BreakableString text={id} />
            </Link>
          )}
        </TooltipTrigger>
        <TooltipContent>
          {linksProject
            ? 'Inspect the project details in projects table'
            : 'Inspect the package details in packages table'}
        </TooltipContent>
      </Tooltip>
      <CopyToClipboard copyText={id} className={copyClassName} />
    </>
  );
};
