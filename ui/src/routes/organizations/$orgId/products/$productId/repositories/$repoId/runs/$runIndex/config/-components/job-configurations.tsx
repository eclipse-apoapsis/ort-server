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

import { OrtRun, PreconfiguredPluginDescriptor } from '@/api';
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion';
import { ConfigFields, ConfigRow } from './config-fields';
import { jobSections, PluginDescriptorsContext } from './config-utils';
import { ConfigValue } from './config-value';
import { JobTitle } from './job-title';

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

  return (
    <PluginDescriptorsContext.Provider value={plugins}>
      <Accordion
        type='multiple'
        value={openSections}
        onValueChange={onOpenSectionsChange}
      >
        {jobSections.map(({ value, title }) => {
          const jobConfig = jobConfigs[value];

          return (
            <AccordionItem key={value} value={value}>
              <AccordionTrigger>
                <JobTitle title={title} job={run.jobs[value]} />
              </AccordionTrigger>
              <AccordionContent className='pl-6'>
                {jobConfig && (
                  <div className='flex flex-col gap-3'>
                    {value === 'evaluator' && (
                      <dl>
                        <ConfigRow label='Rule set'>
                          <ConfigValue
                            value={jobConfigs.ruleSet}
                            path={['ruleSet']}
                          />
                        </ConfigRow>
                      </dl>
                    )}
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
                  </div>
                )}
              </AccordionContent>
            </AccordionItem>
          );
        })}
      </Accordion>
    </PluginDescriptorsContext.Provider>
  );
};
