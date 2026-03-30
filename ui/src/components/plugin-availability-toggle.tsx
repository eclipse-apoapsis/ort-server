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

import { PluginAvailability } from '@/api';
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group.tsx';

type PluginAvailabilityToggleProps = {
  availability: PluginAvailability;
  onAvailabilityChange: (availability: PluginAvailability) => void;
  disabled?: boolean;
};

export function PluginAvailabilityToggle({
  availability,
  onAvailabilityChange,
  disabled,
}: PluginAvailabilityToggleProps) {
  return (
    <ToggleGroup
      type='single'
      variant='outline'
      value={availability}
      onValueChange={(value) => {
        // Guard against deselection: ToggleGroup emits an empty string when
        // the currently active item is clicked again.
        if (value) onAvailabilityChange(value as PluginAvailability);
      }}
      disabled={disabled}
      className='gap-0'
    >
      <ToggleGroupItem
        value='ENABLED'
        className='rounded-r-none data-[state=on]:bg-green-500 data-[state=on]:text-white'
      >
        Enabled
      </ToggleGroupItem>
      <ToggleGroupItem
        value='RESTRICTED'
        className='-ml-px rounded-none data-[state=on]:bg-amber-500 data-[state=on]:text-white'
      >
        Restricted
      </ToggleGroupItem>
      <ToggleGroupItem
        value='DISABLED'
        className='-ml-px rounded-l-none data-[state=on]:bg-red-500 data-[state=on]:text-white'
      >
        Disabled
      </ToggleGroupItem>
    </ToggleGroup>
  );
}
