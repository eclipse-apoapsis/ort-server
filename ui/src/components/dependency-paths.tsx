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

import { MoveRight } from 'lucide-react';

import { Package, ShortestDependencyPath } from '@/api/requests';
import {
  identifierToPurl,
  identifierToString,
} from '@/helpers/identifier-conversion.ts';
import { cn } from '@/lib/utils';
import { PackageIdType } from '@/schemas';

type DependencyPathsProps = {
  pkg: Package;
  pkgIdType?: PackageIdType;
  className?: string;
};

export const DependencyPaths = ({
  pkg,
  pkgIdType,
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
  className,
}: {
  shortestPath: ShortestDependencyPath;
  pkg: Package;
  pkgIdType?: PackageIdType;
  className: string;
}) => {
  return (
    <div className={cn('flex flex-wrap gap-x-2 align-middle', className)}>
      <div>{identifierToString(shortestPath.projectIdentifier)}</div>
      <div className='text-muted-foreground'>
        (scope '{shortestPath.scope}')
      </div>
      {shortestPath.path.map((path, index) => (
        <div className='flex flex-wrap gap-x-2 align-middle' key={index}>
          <MoveRight size={20} />
          <div>
            {pkgIdType === 'ORT_ID'
              ? identifierToString(path)
              : identifierToPurl(path)}
          </div>
        </div>
      ))}
      <div className='flex flex-wrap gap-x-2 align-middle'>
        <MoveRight size={20} />
        <div>
          {pkgIdType === 'ORT_ID'
            ? identifierToString(pkg.identifier)
            : pkg.purl}
        </div>
      </div>
    </div>
  );
};
