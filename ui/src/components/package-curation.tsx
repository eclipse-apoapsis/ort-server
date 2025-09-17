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

import { PackageCurationData } from '@/api';
import { RenderProperty } from '@/components/render-property';
import { Card, CardContent, CardHeader } from '@/components/ui/card';

type PackageCurationProps = {
  curation: PackageCurationData;
};

export const PackageCuration = ({ curation }: PackageCurationProps) => {
  // This is used when deciding if the curation has any data to display.
  // The logic is needed, as the curation object is not totally nullable,
  // but the declaredLicenseMapping is an empty map when not set.
  const curationToDisplay = {
    ...curation,
    comment: undefined,
    declaredLicenseMapping:
      Object.keys(curation.declaredLicenseMapping).length > 0
        ? curation.declaredLicenseMapping
        : undefined,
  };

  return (
    <Card className='my-2 w-full'>
      <CardHeader className='flex gap-2'>
        <RenderProperty
          label='Comment'
          value={curation.comment}
          showIfEmpty={false}
          type='textblock'
        />
      </CardHeader>
      {curationToDisplay &&
        Object.keys(curationToDisplay).some(
          (key) =>
            curationToDisplay[key as keyof typeof curationToDisplay] !==
            undefined
        ) && (
          <CardContent>
            <div className='flex flex-col gap-4'>
              <RenderProperty
                label='Authors'
                value={curation.authors}
                showIfEmpty={false}
              />
              <RenderProperty
                label='Description'
                value={curation.description}
                showIfEmpty={false}
              />
              <RenderProperty
                label='Homepage'
                value={curation.homepageUrl}
                type='url'
                showIfEmpty={false}
              />
              <RenderProperty
                label='CPE'
                value={curation.cpe}
                showIfEmpty={false}
              />
              {curation.vcs && (
                <div>
                  <div className='font-semibold'>VCS</div>
                  <div className='ml-2'>
                    <RenderProperty
                      label='Type'
                      value={curation.vcs.type}
                      showIfEmpty={false}
                    />
                    <RenderProperty
                      label='URL'
                      value={curation.vcs.url}
                      type='url'
                      showIfEmpty={false}
                    />
                    <RenderProperty
                      label='Revision'
                      value={curation.vcs.revision}
                      showIfEmpty={false}
                    />
                    <RenderProperty
                      label='Path'
                      value={curation.vcs.path}
                      showIfEmpty={false}
                    />
                  </div>
                </div>
              )}
              {curation.binaryArtifact && (
                <div>
                  <div className='font-semibold'>Binary Artifact</div>
                  <div className='ml-2'>
                    <RenderProperty
                      label='URL'
                      value={curation.binaryArtifact.url}
                      type='url'
                      showIfEmpty={false}
                    />
                    <RenderProperty
                      label='Hash value'
                      value={curation.binaryArtifact.hashValue}
                      showIfEmpty={false}
                    />
                    <RenderProperty
                      label='Hash algorithm'
                      value={curation.binaryArtifact.hashAlgorithm}
                      showIfEmpty={false}
                    />
                  </div>
                </div>
              )}
              {curation.sourceArtifact && (
                <div>
                  <div className='font-semibold'>Source Artifact</div>
                  <div className='ml-2'>
                    <RenderProperty
                      label='URL'
                      value={curation.sourceArtifact.url}
                      type='url'
                      showIfEmpty={false}
                    />
                    <RenderProperty
                      label='Hash value'
                      value={curation.sourceArtifact.hashValue}
                      showIfEmpty={false}
                    />
                    <RenderProperty
                      label='Hash algorithm'
                      value={curation.sourceArtifact.hashAlgorithm}
                      showIfEmpty={false}
                    />
                  </div>
                </div>
              )}
              {Object.keys(curation.declaredLicenseMapping).length > 0 && (
                <RenderProperty
                  label='Declared license mapping'
                  value={curation.declaredLicenseMapping}
                  type='keyvalue'
                  showIfEmpty={false}
                />
              )}
              <RenderProperty
                label='Concluded license'
                value={curation.concludedLicense}
                showIfEmpty={false}
              />
              <RenderProperty
                label='Metadata only'
                value={curation.isMetadataOnly}
                showIfEmpty={false}
              />
              <RenderProperty
                label='Modified'
                value={curation.isModified}
                showIfEmpty={false}
              />
              <RenderProperty
                label='PURL'
                value={curation.purl}
                showIfEmpty={false}
              />
            </div>
          </CardContent>
        )}
    </Card>
  );
};
