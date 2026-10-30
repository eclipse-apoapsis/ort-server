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

import { InfoIcon } from 'lucide-react';

import { OrtRun, PreconfiguredPluginDescriptor } from '@/api';
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion';
import { getJobState, isJobExpandable } from '@/helpers/job-helpers';
import { ConfigFields } from './config-fields';
import { jobSections, PluginDescriptorsContext } from './config-utils';
import { summarizeJob } from './job-summary';
import { JobTitle } from './job-title';

/**
 * Explain why a run shows its requested configuration, or nothing if it is resolved.
 */
const unresolvedNotice = (run: OrtRun) => {
  if (run.resolvedJobConfigs) return undefined;

  switch (run.status) {
    case 'CREATED':
    case 'ACTIVE':
      return 'The server has not resolved this configuration yet. These are the requested settings; the server may still change them.';
    case 'FAILED':
      return 'The server could not resolve this configuration. These are the requested settings.';
    default:
      return 'The resolved configuration of this run is not available. These are the requested settings.';
  }
};

type JobConfigurationsProps = {
  run: OrtRun;
  plugins: PreconfiguredPluginDescriptor[];
  openSections: string[];
  onOpenSectionsChange: (sections: string[]) => void;
};

/**
 * The configuration of each job of a run. A run whose configuration is not resolved shows the
 * requested configuration.
 */
export const JobConfigurations = ({
  run,
  plugins,
  openSections,
  onOpenSectionsChange,
}: JobConfigurationsProps) => {
  const jobConfigs = run.resolvedJobConfigs ?? run.jobConfigs;
  const notice = unresolvedNotice(run);

  return (
    <PluginDescriptorsContext.Provider value={plugins}>
      {notice && (
        <p
          role='status'
          className='bg-muted/50 mb-2 flex items-start gap-2 rounded-md border p-3 text-sm'
        >
          <InfoIcon aria-hidden className='mt-0.5 size-4 shrink-0' />
          {notice}
        </p>
      )}
      <Accordion
        type='multiple'
        value={openSections.filter((section) =>
          jobSections.some(
            ({ value }) => value === section && isJobExpandable(run, value)
          )
        )}
        onValueChange={onOpenSectionsChange}
      >
        {jobSections.map(({ value, title }) => {
          const jobConfig = jobConfigs[value];
          const state = getJobState(run, value);

          return (
            <AccordionItem
              key={value}
              value={value}
              disabled={!isJobExpandable(run, value)}
            >
              <AccordionTrigger>
                <JobTitle
                  title={title}
                  job={run.jobs[value]}
                  state={state}
                  summary={summarizeJob(jobConfigs, value)}
                />
              </AccordionTrigger>
              <AccordionContent className='pl-6'>
                {jobConfig && (
                  <ConfigFields
                    value={jobConfig}
                    path={[value]}
                    hiddenKeys={
                      // The reporter uses the package configuration providers of the
                      // evaluator if the run has one, as on the create-run form.
                      value === 'reporter' && jobConfigs.evaluator
                        ? ['packageConfigurationProviders']
                        : []
                    }
                  />
                )}
              </AccordionContent>
            </AccordionItem>
          );
        })}
      </Accordion>
    </PluginDescriptorsContext.Provider>
  );
};
