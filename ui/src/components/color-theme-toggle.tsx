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

import { Check } from 'lucide-react';

import { useTheme } from '@/components/theme-provider-context';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { colorThemes } from '@/lib/types';

/**
 * 2×2 color chip preview for a SPECIFIC theme.
 * It scopes CSS variables by placing `data-theme={themeId}` on a wrapper,
 * so the preview uses that theme's colors only inside the chip.
 */
function ThemeChip({
  themeId,
  vars = ['--primary', '--secondary', '--card', '--destructive'],
  className = '',
}: {
  themeId: string;
  vars?: [string, string, string, string];
  className?: string;
}) {
  return (
    <div
      data-theme={themeId}
      className={`grid grid-cols-2 grid-rows-2 overflow-hidden rounded-[0.25rem] ${className}`}
      aria-hidden
      // a tiny ring so chips are visible on very light/dark menus
      style={{ boxShadow: '0 0 0 1px rgb(0 0 0 / 0.08) inset' }}
    >
      {vars.map((cssVar, i) => (
        <div
          key={i}
          className='border border-black/10'
          style={{ backgroundColor: `var(${cssVar})` }}
          title={`${themeId} ${cssVar}`}
        />
      ))}
    </div>
  );
}

export function ColorThemeToggle() {
  const { colorTheme, setColorTheme } = useTheme();

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant='outline' size='icon' className='relative'>
          {/* Show chip of the CURRENT theme in the button */}
          <ThemeChip themeId={colorTheme} className='size-4' />
          <span className='sr-only'>Toggle color theme</span>
        </Button>
      </DropdownMenuTrigger>

      <DropdownMenuContent align='end'>
        {colorThemes.map((theme) => {
          const label = (
            theme.charAt(0).toUpperCase() + theme.slice(1)
          ).replace('-', ' ');
          const isActive = theme === colorTheme;

          return (
            <DropdownMenuItem
              key={theme}
              onClick={() => setColorTheme(theme)}
              className='flex items-center justify-between gap-3'
            >
              <div className='flex items-center gap-3'>
                {/* Preview chip for THIS option only */}
                <ThemeChip themeId={theme} className='size-4' />
                <span>{label}</span>
              </div>
              {isActive ? (
                <Check className='size-4 opacity-80' />
              ) : (
                <span className='size-4' />
              )}
            </DropdownMenuItem>
          );
        })}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
