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

import { z } from 'zod';

const emptyStringToUndefined = z.literal('').transform(() => undefined);

// This function will allow to have optional fields in the form, which also
// contain restricting Zod validation schemas.
//
// Example usage:
//
// const formSchema = z.object({
//   name: asOptionalField(z.string().min(5)),
// });
//
// defines "name" as an optional field, so it can be empty (transforming to undefined),
// but if it is not empty, it must be at least 5 characters long.
export function asOptionalField<T extends z.ZodTypeAny>(schema: T) {
  return schema.optional().or(emptyStringToUndefined);
}
