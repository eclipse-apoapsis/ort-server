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

import { OrtRun, PostRepositoryRun } from '@/api';

/**
 * Rebuild the request that created the run, in the shape the create-run form produces, so it
 * can be sent again through the API or the CLI. Settings the run does not have are left out.
 */
export const runToPayload = (run: OrtRun): PostRepositoryRun => ({
  revision: run.revision,
  ...(run.path ? { path: run.path } : {}),
  ...(run.paths ? { paths: run.paths } : {}),
  ...(run.scopes ? { scopes: run.scopes } : {}),
  ...(run.jobConfigContext ? { jobConfigContext: run.jobConfigContext } : {}),
  ...(run.environmentConfigPath
    ? { environmentConfigPath: run.environmentConfigPath }
    : {}),
  labels: run.labels,
  jobConfigs: run.jobConfigs,
});
