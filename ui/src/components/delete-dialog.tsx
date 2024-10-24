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

import { Loader2, OctagonAlert, TrashIcon } from 'lucide-react';
import { useEffect, useState } from 'react';

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
import { cn } from '@/lib/utils';

interface DeleteDialogProps {
  open?: boolean;
  setOpen?: React.Dispatch<React.SetStateAction<boolean>>;
  item: {
    descriptor: string;
    name: string;
  };
  onDelete: () => void;
  isPending: boolean;
  className?: string;
  textConfirmation?: boolean;
}

export const DeleteDialog = ({
  open,
  setOpen,
  item,
  onDelete,
  isPending,
  className,
  textConfirmation = false,
}: DeleteDialogProps) => {
  const [input, setInput] = useState('');
  const isDeleteDisabled = textConfirmation && input !== item.name;

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
          <AlertDialogTrigger asChild>
            <Button
              size='sm'
              variant='outline'
              className={cn('h-9 px-2', className)}
            >
              <span className='sr-only'>Delete</span>
              <TrashIcon size={16} />
            </Button>
          </AlertDialogTrigger>
        </TooltipTrigger>
        <TooltipContent>Delete this {item.descriptor}</TooltipContent>
      </Tooltip>
      {/* Adding the preventDefault will prevent focusing on the trigger after closing the modal (which would cause the tooltip to show) */}
      <AlertDialogContent onCloseAutoFocus={(e) => e.preventDefault()}>
        <AlertDialogHeader>
          <div className='flex items-center'>
            <OctagonAlert className='h-8 w-8 pr-2 text-red-500' />
            <AlertDialogTitle>Delete {item.descriptor}</AlertDialogTitle>
          </div>
        </AlertDialogHeader>
        {textConfirmation ? (
          <AlertDialogDescription>
            <div className='flex flex-col gap-2'>
              <div>
                Are you sure you want to delete this {item.descriptor}:{' '}
                <span className='font-bold'>{item.name}</span>?
              </div>
              <div>
                Deleting might have unwanted results and side effects, and the
                deletion is irreversible. Please type{' '}
                <span className='font-bold'>{item.name}</span> below to confirm
                deletion.
              </div>
              <Input
                autoFocus
                value={input}
                onChange={(e) => setInput(e.target.value)}
              />
            </div>
          </AlertDialogDescription>
        ) : (
          <AlertDialogDescription>
            Are you sure you want to delete this {item.descriptor}:{' '}
            <span className='font-bold'>{item.name}</span>?
          </AlertDialogDescription>
        )}
        <AlertDialogFooter>
          <AlertDialogCancel>Cancel</AlertDialogCancel>
          <Button
            disabled={isDeleteDisabled}
            onClick={onDelete}
            className='bg-red-500'
          >
            {isPending ? (
              <>
                <span className='sr-only'>Deleting {item.descriptor}...</span>
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
