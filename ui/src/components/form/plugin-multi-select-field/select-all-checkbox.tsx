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

import type { CheckedState } from '@radix-ui/react-checkbox';
import { useId } from 'react';

import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';

type SelectAllCheckboxProps = {
  /** Checked when all plugins are enabled, indeterminate when some are. */
  state: CheckedState;
  onChange: (selected: boolean) => void;
};

export const SelectAllCheckbox = ({
  state,
  onChange,
}: SelectAllCheckboxProps) => {
  // Several plugin fields can be shown at once, so each needs its own id.
  const id = useId();

  return (
    <div className='flex items-center space-x-3'>
      <Checkbox
        id={id}
        checked={state}
        onCheckedChange={(checked) => onChange(checked === true)}
      />
      <Label htmlFor={id} className='font-bold'>
        Enable/disable all
      </Label>
    </div>
  );
};
