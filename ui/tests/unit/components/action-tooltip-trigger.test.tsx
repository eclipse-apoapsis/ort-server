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

// @vitest-environment jsdom

import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { ActionTooltipTrigger } from '@/components/action-tooltip-trigger';
import { Button } from '@/components/ui/button';
import { Tooltip, TooltipContent } from '@/components/ui/tooltip';

const renderAction = (disabled: boolean) =>
  render(
    <Tooltip>
      <ActionTooltipTrigger disabled={disabled} className='ml-auto'>
        {/* The styles of the application make a disabled control ignore the pointer. */}
        <Button
          disabled={disabled}
          style={disabled ? { pointerEvents: 'none' } : undefined}
        >
          Add
        </Button>
      </ActionTooltipTrigger>
      <TooltipContent>
        {disabled ? 'Insufficient permissions.' : 'Add something'}
      </TooltipContent>
    </Tooltip>
  );

describe('ActionTooltipTrigger', () => {
  it('shows the tooltip of a disabled action when the pointer is over it', async () => {
    const user = userEvent.setup();
    renderAction(true);

    const wrapper = screen.getByRole('button', { name: 'Add' }).parentElement!;
    expect(wrapper).toHaveClass('ml-auto');

    await user.hover(wrapper);

    expect(
      await screen.findByRole('tooltip', { name: 'Insufficient permissions.' })
    ).toBeInTheDocument();
  });

  it('opens the tooltip from an enabled control itself', async () => {
    const user = userEvent.setup();
    renderAction(false);

    await user.hover(screen.getByRole('button', { name: 'Add' }));

    expect(
      await screen.findByRole('tooltip', { name: 'Add something' })
    ).toBeInTheDocument();
  });
});
