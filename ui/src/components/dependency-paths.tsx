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
import { MoveRight } from 'lucide-react';

import { Package, ShortestDependencyPath } from '@/api';
import {
  getExactPackageLinkSearch,
  identifierToPurl,
  identifierToString,
} from '@/helpers/identifier-conversion.ts';
import { cn } from '@/lib/utils';
import { PackageIdType, packageIdTypeSchema } from '@/schemas';

type RunParams = {
  orgId: string;
  productId: string;
  repoId: string;
  runIndex: string;
};

type DependencyPathsProps = {
  pkg: Package;
  pkgIdType?: PackageIdType;
  params: RunParams;
  className?: string;
};

const linkClassName = 'text-left text-blue-400 hover:underline';

// Shows the shortest paths from the projects of a run to a package. The
// project and the packages on the way link to their rows in the projects and
// packages tables of the run.
export const DependencyPaths = ({
  pkg,
  pkgIdType,
  params,
  className,
}: DependencyPathsProps) => {
  return (
    <div className={className}>
      {pkg.shortestDependencyPaths.map((path, index) => (
        <div key={index}>
          <DependencyPath
            shortestPath={path}
            pkg={pkg}
            pkgIdType={pkgIdType}
            params={params}
            className='ml-2'
          />
        </div>
      ))}
    </div>
  );
};

const DependencyPath = ({
  shortestPath,
  pkg,
  pkgIdType,
  params,
  className,
}: {
  shortestPath: ShortestDependencyPath;
  pkg: Package;
  pkgIdType?: PackageIdType;
  params: RunParams;
  className: string;
}) => {
  const projectId = identifierToString(shortestPath.projectIdentifier);

  return (
    <div className={cn('flex flex-wrap gap-x-2 align-middle', className)}>
      <Link
        className={linkClassName}
        to='/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/projects'
        params={params}
        search={{
          projectId,
          projectIdMatchType: 'exact',
          marked: projectId,
        }}
      >
        {projectId}
      </Link>
      <div className='text-muted-foreground'>
        (scope '{shortestPath.scope}')
      </div>
      {shortestPath.path.map((path, index) => (
        <div className='flex flex-wrap gap-x-2 align-middle' key={index}>
          <MoveRight size={20} />
          <Link
            className={linkClassName}
            to='/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/packages'
            params={params}
            search={getExactPackageLinkSearch(path)}
          >
            {pkgIdType === packageIdTypeSchema.enum.ORT_ID
              ? identifierToString(path)
              : identifierToPurl(path)}
          </Link>
        </div>
      ))}
      <div className='flex flex-wrap gap-x-2 align-middle'>
        <MoveRight size={20} />
        <div>
          {pkgIdType === packageIdTypeSchema.enum.ORT_ID
            ? identifierToString(pkg.identifier)
            : pkg.purl}
        </div>
      </div>
    </div>
  );
};
