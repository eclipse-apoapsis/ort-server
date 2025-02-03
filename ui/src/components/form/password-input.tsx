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

import { Eye, EyeOff } from 'lucide-react';
import { useState } from 'react';

import { Input } from '@/components/ui/input.tsx';

export const PasswordInput = ({ ...props }) => {
  const [showPassword, setShowPassword] = useState(false);

  return (
    <div className='relative'>
      <Input type={showPassword ? 'text' : 'password'} {...props} />
      <button
        type='button'
        className='absolute inset-y-0 right-0 flex items-center px-2'
        onClick={() => setShowPassword(!showPassword)}
      >
        {showPassword ? (
          <EyeOff className='h-5 w-5' />
        ) : (
          <Eye className='h-5 w-5' />
        )}
      </button>
    </div>
  );
};
