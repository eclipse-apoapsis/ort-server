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

import { createEnv } from '@t3-oss/env-core';
import { z } from 'zod';

export const env = createEnv({
  clientPrefix: 'VITE_',
  client: {
    VITE_BASEPATH: z.string().default('/'),
    VITE_API_URL: z.string().default('http://localhost:8080'),
    VITE_AUTHORITY: z.string().default('http://localhost:8081/realms/master'),
    VITE_UI_URL: z.string().default('http://localhost:5173/'),
    // Client IDs for the Keycloak clients
    VITE_CLIENT_ID: z.string().default('ort-server-ui-dev'),
    VITE_RUN_POLL_INTERVAL: z.coerce.number().default(10000),
    VITE_OIDC_LOG_LEVEL: z
      .enum(['NONE', 'ERROR', 'WARN', 'INFO'])
      .default('NONE'),
  },
  runtimeEnv: import.meta.env,
  emptyStringAsUndefined: true,
});
