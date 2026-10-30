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

import { OrtRun } from '@/api';
import { Sha1Component } from '@/components/sha1-component';
import {
  includesExcludesField,
  jobConfigFields,
  runConfigFields,
} from '@/lib/job-config-fields';
import { ConfigGroup, ConfigList, ConfigRow } from './config-fields';
import { ConfigValue, NoneValue } from './config-value';
import { RunIncludesExcludes } from './run-includes-excludes';

type RunSettingsProps = {
  run: OrtRun;
};

/**
 * The settings of a run outside its job configurations. The rule set and the parameters come
 * from the same configuration as the job settings: the resolved one, or the requested one if
 * the configuration is not resolved.
 */
export const RunSettings = ({ run }: RunSettingsProps) => {
  const jobConfigs = run.resolvedJobConfigs ?? run.jobConfigs;
  const hasIncludesExcludes = [run.scopes, run.paths].some(
    (entries) =>
      (entries?.includes?.length ?? 0) > 0 ||
      (entries?.excludes?.length ?? 0) > 0
  );

  return (
    <ConfigList>
      <ConfigRow {...runConfigFields.revision}>
        <span className='inline-flex flex-wrap items-center gap-2'>
          <span>{run.revision}</span>
          {run.resolvedRevision && run.resolvedRevision !== run.revision && (
            <Sha1Component sha1={run.resolvedRevision} />
          )}
        </span>
      </ConfigRow>
      <ConfigRow {...runConfigFields.path}>
        <ConfigValue value={run.path} path={['path']} />
      </ConfigRow>
      <ConfigRow {...runConfigFields.jobConfigContext}>
        {run.jobConfigContext || run.resolvedJobConfigContext ? (
          <span className='inline-flex flex-wrap items-center gap-2'>
            {run.jobConfigContext && <span>{run.jobConfigContext}</span>}
            {run.resolvedJobConfigContext &&
              run.resolvedJobConfigContext !== run.jobConfigContext && (
                <Sha1Component sha1={run.resolvedJobConfigContext} />
              )}
          </span>
        ) : (
          <NoneValue />
        )}
      </ConfigRow>
      <ConfigRow {...runConfigFields.environmentConfigPath}>
        <ConfigValue
          value={run.environmentConfigPath}
          path={['environmentConfigPath']}
        />
      </ConfigRow>
      <ConfigRow {...jobConfigFields.ruleSet}>
        <ConfigValue value={jobConfigs.ruleSet} path={['ruleSet']} />
      </ConfigRow>
      <ConfigRow {...jobConfigFields.parameters}>
        <ConfigValue value={jobConfigs.parameters} path={['parameters']} />
      </ConfigRow>
      <ConfigRow {...runConfigFields.labels}>
        <ConfigValue value={run.labels} path={['labels']} />
      </ConfigRow>
      {hasIncludesExcludes ? (
        <ConfigGroup {...includesExcludesField}>
          <RunIncludesExcludes scopes={run.scopes} paths={run.paths} />
        </ConfigGroup>
      ) : (
        <ConfigRow {...includesExcludesField}>
          <NoneValue />
        </ConfigRow>
      )}
    </ConfigList>
  );
};
