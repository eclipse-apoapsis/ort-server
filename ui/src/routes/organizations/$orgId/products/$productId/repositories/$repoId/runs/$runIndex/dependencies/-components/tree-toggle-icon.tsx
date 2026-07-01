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

import { SquareMinus, SquarePlus } from 'lucide-react';

/**
 * State-aware expand/collapse indicator for the dependency tree.
 * Shows a "SquarePlus" icon when the nearest collapsible ancestor is closed
 * and a "SquareMinus" icon when it is open.
 */
export const TreeToggleIcon = () => (
  <>
    <SquarePlus className='text-muted-foreground mt-[3px] size-4 shrink-0 group-data-[state=open]/toggle:hidden' />
    <SquareMinus className='text-muted-foreground mt-[3px] size-4 shrink-0 group-data-[state=closed]/toggle:hidden' />
  </>
);
