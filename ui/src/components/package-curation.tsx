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

import { PackageCuration as Curation } from '@/api';
import { RenderProperty } from '@/components/render-property';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader } from '@/components/ui/card';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { getIssueSeverityBackgroundColor } from '@/helpers/get-status-class';

type PackageCurationProps = {
  curation: Curation;
};

export const PackageCuration = ({ curation }: PackageCurationProps) => {
  // This is used when deciding if the curation has any data to display.
  // The logic is needed, as the curation object is not totally nullable,
  // but the declaredLicenseMapping is an empty map when not set.
  const curationToDisplay = {
    ...curation,
    comment: undefined,
    declaredLicenseMapping:
      curation.data.declaredLicenseMapping &&
      Object.keys(curation.data.declaredLicenseMapping).length > 0
        ? curation.data.declaredLicenseMapping
        : undefined,
  };

  return (
    <Card className='my-2 w-full'>
      <CardHeader className='flex flex-row justify-between gap-2 align-top'>
        <RenderProperty
          label='Comment'
          value={curation.data.comment}
          showIfEmpty={false}
          type='textblock'
        />
        <Tooltip>
          <TooltipTrigger>
            <Badge
              variant='small'
              className={`${getIssueSeverityBackgroundColor('HINT')}`}
            >
              {curation.providerName}
            </Badge>
          </TooltipTrigger>
          <TooltipContent>The provider of the curation.</TooltipContent>
        </Tooltip>
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
                value={curation.data.authors}
                showIfEmpty={false}
              />
              <RenderProperty
                label='Description'
                value={curation.data.description}
                showIfEmpty={false}
              />
              <RenderProperty
                label='Homepage'
                value={curation.data.homepageUrl}
                type='url'
                showIfEmpty={false}
              />
              <RenderProperty
                label='CPE'
                value={curation.data.cpe}
                showIfEmpty={false}
              />
              <RenderProperty
                label='Source Code Origins'
                value={curation.data.sourceCodeOrigins}
                showIfEmpty={false}
              />
              {curation.data.vcs && (
                <div>
                  <div className='font-semibold'>VCS</div>
                  <div className='ml-2'>
                    <RenderProperty
                      label='Type'
                      value={curation.data.vcs.type}
                      showIfEmpty={false}
                    />
                    <RenderProperty
                      label='URL'
                      value={curation.data.vcs.url}
                      type='url'
                      showIfEmpty={false}
                    />
                    <RenderProperty
                      label='Revision'
                      value={curation.data.vcs.revision}
                      showIfEmpty={false}
                    />
                    <RenderProperty
                      label='Path'
                      value={curation.data.vcs.path}
                      showIfEmpty={false}
                    />
                  </div>
                </div>
              )}
              {curation.data.binaryArtifact && (
                <div>
                  <div className='font-semibold'>Binary Artifact</div>
                  <div className='ml-2'>
                    <RenderProperty
                      label='URL'
                      value={curation.data.binaryArtifact.url}
                      type='url'
                      showIfEmpty={false}
                    />
                    <RenderProperty
                      label='Hash Value'
                      value={curation.data.binaryArtifact.hashValue}
                      showIfEmpty={false}
                    />
                    <RenderProperty
                      label='Hash Algorithm'
                      value={curation.data.binaryArtifact.hashAlgorithm}
                      showIfEmpty={false}
                    />
                  </div>
                </div>
              )}
              {curation.data.sourceArtifact && (
                <div>
                  <div className='font-semibold'>Source Artifact</div>
                  <div className='ml-2'>
                    <RenderProperty
                      label='URL'
                      value={curation.data.sourceArtifact.url}
                      type='url'
                      showIfEmpty={false}
                    />
                    <RenderProperty
                      label='Hash Value'
                      value={curation.data.sourceArtifact.hashValue}
                      showIfEmpty={false}
                    />
                    <RenderProperty
                      label='Hash Algorithm'
                      value={curation.data.sourceArtifact.hashAlgorithm}
                      showIfEmpty={false}
                    />
                  </div>
                </div>
              )}
              {curation.data.declaredLicenseMapping &&
                Object.keys(curation.data.declaredLicenseMapping).length >
                  0 && (
                  <RenderProperty
                    label='Declared License Mapping'
                    value={curation.data.declaredLicenseMapping}
                    type='keyvalue'
                    useArrowsInKeyValue
                    showIfEmpty={false}
                  />
                )}
              <RenderProperty
                label='Concluded License'
                value={curation.data.concludedLicense}
                showIfEmpty={false}
              />
              <RenderProperty
                label='Metadata Only'
                value={curation.data.isMetadataOnly}
                showIfEmpty={false}
              />
              <RenderProperty
                label='Modified'
                value={curation.data.isModified}
                showIfEmpty={false}
              />
              <RenderProperty
                label='PURL'
                value={curation.data.purl}
                showIfEmpty={false}
              />
              <RenderProperty
                label='Labels'
                value={curation.data.labels}
                type='keyvalue'
                showIfEmpty={false}
              />
            </div>
          </CardContent>
        )}
    </Card>
  );
};
