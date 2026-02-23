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

import { Loader2, OctagonAlert } from 'lucide-react';
import { ReactNode, useEffect, useState } from 'react';

import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';

interface MoveDialogProps {
  /**
   * The name of the thing to move. This is used as part of the description.
   */
  thingName: ReactNode;

  /**
   * An optional ID for the thing to move. If set, this is used to confirm the
   * move by the user by entering the ID.
   */
  thingId?: string;

  /**
   * The UI component to show as part of the move dialog.
   */
  uiComponent: ReactNode;

  /**
   * The action to perform on moving.
   */
  onMove: () => void | Promise<void>;

  /**
   * The tooltip for the move button.
   */
  tooltip?: string;

  /**
   * The title of the move dialog.
   */
  title?: string;
}

export const MoveDialog = ({
  thingName,
  thingId,
  uiComponent,
  onMove,
  tooltip,
  title,
}: MoveDialogProps) => {
  const [input, setInput] = useState('');
  const [open, setOpen] = useState(false);
  const [isPending, setIsPending] = useState(false);
  const isDeleteDisabled = thingId ? input !== thingId : false;

  // Reset the input field whenever the dialog is opened/closed
  useEffect(() => {
    if (open) {
      setInput('');
    }
  }, [open]);

  return (
    <AlertDialog open={open} onOpenChange={setOpen}>
      <Tooltip delayDuration={300}>
        <TooltipTrigger asChild>
          <AlertDialogTrigger asChild>{uiComponent}</AlertDialogTrigger>
        </TooltipTrigger>
        <TooltipContent>{tooltip || 'Delete'}</TooltipContent>
      </Tooltip>
      {/* Adding the preventDefault will prevent focusing on the trigger after closing the modal (which would cause the tooltip to show) */}
      <AlertDialogContent onCloseAutoFocus={(e) => e.preventDefault()}>
        <AlertDialogHeader>
          <div className='flex items-center'>
            <OctagonAlert className='h-8 w-8 pr-2 text-red-500' />
            <AlertDialogTitle>{title || 'Confirm Move'}</AlertDialogTitle>
          </div>
        </AlertDialogHeader>
        <div className='flex flex-col gap-2 overflow-auto wrap-break-word'>
          <AlertDialogDescription>
            All user access roles assigned directly to the repository will be
            removed as part of the move operation.
          </AlertDialogDescription>
          {thingId && (
            <>
              <AlertDialogDescription>
                If you are sure to move the {thingName}{' '}
                <span className='font-bold'>{thingId}</span>, enter the bold
                text below for confirmation.
              </AlertDialogDescription>
              <Input
                autoFocus
                value={input}
                onChange={(e) => setInput(e.target.value)}
              />
            </>
          )}
        </div>
        <AlertDialogFooter>
          <AlertDialogCancel>Cancel</AlertDialogCancel>
          <Button
            disabled={isDeleteDisabled}
            onClick={async () => {
              setIsPending(true);
              try {
                await onMove();
              } finally {
                setIsPending(false);
              }
              setOpen(false);
            }}
            className='bg-red-500'
          >
            {isPending ? (
              <>
                <span className='sr-only'>Moving...</span>
                <Loader2 size={16} className='mx-3 animate-spin' />
              </>
            ) : (
              'Move'
            )}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
};
