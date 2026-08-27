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

// The number of items a dropdown asks for at a time. Dropdowns load their content page by page as
// the user scrolls, so this is only the size of one page, not the number of items reachable in it.
export const DROPDOWN_PAGE_SIZE = 50;

// A repository that is just being added cannot have secrets or an infrastructure service yet, so
// fixed names keep them recognizable in the repository's Secrets and Infrastructure Services views.
export const REPOSITORY_USER_SECRET = 'REPOSITORY-USERNAME';
export const REPOSITORY_PASSWORD_SECRET = 'REPOSITORY-PASSWORD';
export const REPOSITORY_ACCESS_SERVICE = 'REPOSITORY-ACCESS';

// The base download URL for the downloadable assets of the latest ORT Server release.
export const ORT_SERVER_GITHUB_RELEASES_BASE_URL =
  'https://github.com/eclipse-apoapsis/ort-server/releases';

export const ACTION_COLUMN_SIZE = 20;
