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

import { TrashIcon } from 'lucide-react';
import { forwardRef } from 'react';

import { Button } from './ui/button';

type DeleteIconButtonProps = {
  disabled?: boolean;
  icon?: React.ReactNode;
  srDescription?: string;
};

const DeleteIconButton = forwardRef<HTMLButtonElement, DeleteIconButtonProps>(
  ({ ...props }, ref) => {
    return (
      <Button
        size='sm'
        variant='outline'
        {...props}
        className='h-8 px-2 text-red-500'
        ref={ref}
      >
        <span className='sr-only'>{props.srDescription || 'Delete'}</span>
        {props.icon || <TrashIcon size={16} />}
      </Button>
    );
  }
);
DeleteIconButton.displayName = 'DeleteIconButton';

export { DeleteIconButton };
