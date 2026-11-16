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

import type { CreateClientConfig } from '@/api/client.gen';
import { config } from '@/config';

/**
 * Return the initial configuration of the generated API client, which calls this function when it
 * is created. The client starts with the API URL of the deployment, so query options get the right
 * URL in their query key even when a module builds them as it is loaded.
 */
export const createClientConfig: CreateClientConfig = (override) => ({
  ...override,
  baseURL: config.API_URL,
  headers: {
    'X-Client-Type': 'ort-server-ui',
  },
});
