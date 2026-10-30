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

import { InfoIcon, ServerIcon } from 'lucide-react';

import { OrtRun, PreconfiguredPluginDescriptor } from '@/api';
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion';
import { getJobState, isJobExpandable } from '@/helpers/job-helpers';
import { ConfigFields } from './config-fields';
import {
  hiddenJobKeys,
  jobSections,
  PluginDescriptorsContext,
} from './config-utils';
import { summarizeJob } from './job-summary';
import { JobTitle } from './job-title';
import {
  countChangesByJob,
  findChange,
  getServerChanges,
  ServerChangesContext,
} from './server-changes';

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

const jobElementId = (job: string) => `job-${job}`;

type ServerChangesSummaryProps = {
  run: OrtRun;
  jobs: ReturnType<typeof countChangesByJob>;
  onShowJob: (job: string) => void;
};

/**
 * What the server changed in the jobs, so that it can be seen while the jobs are collapsed. Each
 * job leads to its settings, unless the job has none to show because the server removed it.
 */
const ServerChangesSummary = ({
  run,
  jobs,
  onShowJob,
}: ServerChangesSummaryProps) => {
  const total = jobs.reduce((sum, { count }) => sum + count, 0);

  if (total === 0) return null;

  return (
    <p className='text-muted-foreground mb-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm'>
      <span className='inline-flex items-center gap-1.5'>
        <ServerIcon aria-hidden className='size-3.5 shrink-0' />
        The server changed {total} {total === 1 ? 'setting' : 'settings'}:
      </span>
      {jobs.map(({ job, title, count }) =>
        isJobExpandable(run, job) ? (
          <button
            key={job}
            type='button'
            className='hover:text-foreground focus-visible:ring-ring/50 rounded-sm underline underline-offset-2 outline-none focus-visible:ring-2'
            onClick={() => onShowJob(job)}
          >
            {title} ({count})
          </button>
        ) : (
          <span key={job}>
            {title} ({count})
          </span>
        )
      )}
    </p>
  );
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
  const changes = getServerChanges(run);

  const showJob = (job: string) => {
    onOpenSectionsChange([...new Set([...openSections, job])]);

    // The job is only ready to take the focus once it has been rendered open.
    requestAnimationFrame(() => {
      const element = document.getElementById(jobElementId(job));

      element?.scrollIntoView({ block: 'start' });
      element?.querySelector('button')?.focus({ preventScroll: true });
    });
  };

  return (
    <ServerChangesContext.Provider value={changes}>
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
        <ServerChangesSummary
          run={run}
          jobs={countChangesByJob(changes, jobConfigs)}
          onShowJob={showJob}
        />
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
                id={jobElementId(value)}
                value={value}
                disabled={!isJobExpandable(run, value)}
                className='scroll-mt-20'
              >
                <AccordionTrigger>
                  <JobTitle
                    title={title}
                    job={run.jobs[value]}
                    state={state}
                    summary={summarizeJob(jobConfigs, value)}
                    added={findChange(changes, [value])?.kind === 'added'}
                  />
                </AccordionTrigger>
                <AccordionContent className='pl-6'>
                  {jobConfig && (
                    <ConfigFields
                      value={jobConfig}
                      path={[value]}
                      hiddenKeys={hiddenJobKeys(jobConfigs, value)}
                    />
                  )}
                </AccordionContent>
              </AccordionItem>
            );
          })}
        </Accordion>
      </PluginDescriptorsContext.Provider>
    </ServerChangesContext.Provider>
  );
};
