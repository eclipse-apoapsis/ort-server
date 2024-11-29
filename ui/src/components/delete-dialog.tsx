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
  description: ReactNode;
  onDelete: () => void | Promise<void>;
  /**
   * Optional confirmation text to be required before deletion. If the text is provided, the user is
   * required to type the text in an input field to confirm the deletion. If the text is not
   * provided, the user can delete the item without any additional confirmation.
   */
  confirmationText?: string;
  trigger: ReactNode;
}

export const DeleteDialog = ({
  onDelete,
  description,
  confirmationText,
  trigger,
}: DeleteDialogProps) => {
  const [input, setInput] = useState('');
  const [open, setOpen] = useState(false);
  const [isPending, setIsPending] = useState(false);
  const isDeleteDisabled = confirmationText
    ? input !== confirmationText
    : false;

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
          <AlertDialogTrigger asChild>{trigger}</AlertDialogTrigger>
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
            <div>{description}</div>
            <div>
              Deleting might have unwanted results and side effects, and the
              deletion is irreversible.
            </div>
            {confirmationText && (
              <>
                <div>
                  Please type{' '}
                  <span className='font-bold'>{confirmationText}</span> below to
                  confirm deletion.
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
