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

interface DeleteDialogProps {
  /**
   * The name of the thing to delete. This is used as part of the description.
   */
  thingName: ReactNode;

  /**
   * An optional ID for the thing to delete. If set, this is used to confirm the
   * deletion by the user by entering the ID.
   */
  thingId?: string;

  /**
   * The UI component to show as part of the delete dialog.
   */
  uiComponent: ReactNode;

  /**
   * The action to perform on deletion.
   */
  onDelete: () => void | Promise<void>;
}

export const DeleteDialog = ({
  thingName,
  thingId,
  uiComponent,
  onDelete,
}: DeleteDialogProps) => {
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
        <TooltipContent>Delete</TooltipContent>
      </Tooltip>
      {/* Adding the preventDefault will prevent focusing on the trigger after closing the modal (which would cause the tooltip to show) */}
      <AlertDialogContent onCloseAutoFocus={(e) => e.preventDefault()}>
        <AlertDialogHeader>
          <div className='flex items-center'>
            <OctagonAlert className='h-8 w-8 pr-2 text-red-500' />
            <AlertDialogTitle>Confirm deletion</AlertDialogTitle>
          </div>
        </AlertDialogHeader>
        <AlertDialogDescription>
          <div className='flex flex-col gap-2'>
            <div>
              Note that deletion is irreversible and might have unwanted side
              effects.
            </div>
            {thingId && (
              <>
                <div>
                  If you are sure to delete the {thingName}{' '}
                  <span className='font-bold'>{thingId}</span>, enter the bold
                  text below for confirmation.
                </div>
                <Input
                  autoFocus
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                />
              </>
            )}
          </div>
        </AlertDialogDescription>
        <AlertDialogFooter>
          <AlertDialogCancel>Cancel</AlertDialogCancel>
          <Button
            disabled={isDeleteDisabled}
            onClick={async () => {
              setIsPending(true);
              try {
                await onDelete();
              } finally {
                setIsPending(false);
              }
              setOpen(false);
            }}
            className='bg-red-500'
          >
            {isPending ? (
              <>
                <span className='sr-only'>Deleting...</span>
                <Loader2 size={16} className='mx-3 animate-spin' />
              </>
            ) : (
              'Delete'
            )}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
};
