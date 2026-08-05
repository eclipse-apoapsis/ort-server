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

// Paginated list queries have a "limit" query parameter, which is the maximum number of items to return.
// When the limit is not set, the queries return a default number of items.
// Some views need every item at once, rather than a page of them, and set the limit to (an
// arbitrary) high number to get them:
// - The issue, rule violation and project tables of a run filter, sort and paginate in the browser,
//   which they cannot stop doing until those endpoints support filtering, see
//   https://github.com/eclipse-apoapsis/ort-server/issues/5349.
// - The secrets and the infrastructure services of a hierarchy are read as one complete list, see
//   https://github.com/eclipse-apoapsis/ort-server/issues/5646.
// Both are costly and meant to go away.
// Dropdowns and other lists the user picks from must not use this. They ask for one page at a time,
// see DROPDOWN_PAGE_SIZE below and the useInfiniteList hook.
export const ALL_ITEMS = 100000;

// The number of items a dropdown asks for at a time. Dropdowns load their content page by page as
// the user scrolls, so this is only the size of one page, not the number of items reachable in it.
export const DROPDOWN_PAGE_SIZE = 50;

// The base download URL for the downloadable assets of the latest ORT Server release.
export const ORT_SERVER_GITHUB_RELEASES_BASE_URL =
  'https://github.com/eclipse-apoapsis/ort-server/releases';

export const ACTION_COLUMN_SIZE = 20;
