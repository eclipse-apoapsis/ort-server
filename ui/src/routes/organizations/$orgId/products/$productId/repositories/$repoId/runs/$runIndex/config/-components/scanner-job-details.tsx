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

import { OrtRun } from '@/api';
import { RenderProperty } from '@/components/render-property';
import { Label } from '@/components/ui/label';

type ScannerJobDetailsProps = {
  run: OrtRun;
};

export const ScannerJobDetails = ({ run }: ScannerJobDetailsProps) => {
  const jobConfigs = run.resolvedJobConfigs?.scanner;

  if (!jobConfigs) return null;

  return (
    <div className='ml-4 flex flex-col gap-4 text-sm'>
      <RenderProperty
        label='Skip concluded'
        value={jobConfigs.skipConcluded}
        showIfEmpty={false}
      />
      <RenderProperty
        label='Skip excluded'
        value={jobConfigs.skipExcluded}
        showIfEmpty={false}
      />
      {jobConfigs.scanners && (
        <div className='space-y-2'>
          <Label className='font-semibold'>Scanners</Label>
          {jobConfigs.scanners.map((scanner) => (
            <div className='ml-2' key={scanner}>
              <Label className='font-semibold'>{scanner}</Label>
              {jobConfigs?.config?.[scanner] && (
                <div className='ml-2'>
                  <Label className='font-semibold'>Configuration</Label>
                  <div className='ml-2'>
                    <RenderProperty
                      label='Options'
                      value={jobConfigs.config?.[scanner].options}
                      type='keyvalue'
                      showIfEmpty={false}
                    />
                    <RenderProperty
                      label='Secrets'
                      value={jobConfigs.config?.[scanner].secrets}
                      type='keyvalue'
                      showIfEmpty={false}
                    />
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
