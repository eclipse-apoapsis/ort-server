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

import { z } from 'zod';

import { FormControl } from '@/components/ui/form';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { getRoleIcon } from '@/helpers/role-helpers.ts';
import { roleSchema } from '@/schemas';

type Role = z.infer<typeof roleSchema>;

type RoleSelectProps = {
  value: Role;
  onChange: (role: Role) => void;
};

/**
 * A field for picking the role to assign to a user in a hierarchy.
 *
 * Render it inside a `FormField`, as it brings the `FormControl` of the field with it.
 */
export const RoleSelect = ({ value, onChange }: RoleSelectProps) => {
  return (
    <Select value={value} onValueChange={onChange}>
      <FormControl>
        <SelectTrigger>
          <SelectValue placeholder='Select a role' />
        </SelectTrigger>
      </FormControl>
      <SelectContent>
        {roleSchema.options.map((role) => (
          <SelectItem key={role} value={role}>
            <div className='flex items-center gap-2'>
              {getRoleIcon(role)}
              {role}
            </div>
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
};
