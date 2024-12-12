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
// As some components, like the item distribution color bar, require all items to calculate the distribution,
// the limit is set to (an arbitrary) high number to ensure that all items are returned.
// This probably needs to be changed in the future to a more sophisticated solution, e.g. by using a separate query
// to get the distribution of items without fetching all items from back-end, which is costly.
export const ALL_ITEMS = 100000;
