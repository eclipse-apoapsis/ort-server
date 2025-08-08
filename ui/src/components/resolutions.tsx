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

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { ItemWithResolutions } from '@/helpers/resolutions';

type ResolutionsProps = {
  item: ItemWithResolutions;
};

export function Resolutions({ item }: ResolutionsProps) {
  return (
    <>
      {item.resolutions && item.resolutions.length > 0 ? (
        item.resolutions.map((resolution) => (
          <Card>
            <CardHeader>
              <CardTitle>{resolution.reason}</CardTitle>
            </CardHeader>
            <CardContent className='flex flex-col gap-2'>
              <div className='italic'>{resolution.comment}</div>
              <div className='flex gap-2'>
                <div className='text-muted-foreground font-semibold'>
                  {'externalId' in resolution
                    ? 'ID Matcher:'
                    : 'Message Matcher:'}
                </div>
                <div className='text-muted-foreground'>
                  {'externalId' in resolution
                    ? resolution.externalId
                    : resolution.message}
                </div>
              </div>
            </CardContent>
          </Card>
        ))
      ) : (
        <div className='text-muted-foreground italic'>No resolutions.</div>
      )}
    </>
  );
}
