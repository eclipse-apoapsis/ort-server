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

import { createFileRoute } from '@tanstack/react-router';
import { Lock } from 'lucide-react';

export const Route = createFileRoute('/403/')({
  component: () => (
    <div className='flex flex-col items-center justify-center'>
      <Lock size={64} className='mt-8' />
      <h1 className='mt-4 text-center text-3xl font-bold'>403 Forbidden</h1>
      <p className='mt-4'>Sorry, you are not allowed to access this page.</p>
    </div>
  ),
});
