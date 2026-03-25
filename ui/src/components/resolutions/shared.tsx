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

import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Pencil } from 'lucide-react';
import { Resolver, useForm } from 'react-hook-form';

import {
  getRunIssuesQueryKey,
  getRunVulnerabilitiesQueryKey,
  patchIssueResolutionForRepositoryMutation,
  patchVulnerabilityResolutionMutation,
  postIssueResolutionForRepositoryMutation,
  postVulnerabilityResolutionMutation,
} from '@/api/@tanstack/react-query.gen';
import {
  IssueResolutionReason,
  PatchIssueResolution,
  PatchVulnerabilityResolution,
  PostIssueResolution,
  PostVulnerabilityResolution,
  VulnerabilityResolutionReason,
} from '@/api/types.gen';
import {
  zIssueResolutionReason,
  zPatchIssueResolution,
  zPatchVulnerabilityResolution,
  zPostIssueResolution,
  zPostVulnerabilityResolution,
  zVulnerabilityResolutionReason,
} from '@/api/zod.gen';
import { DeleteDialog } from '@/components/delete-dialog';
import { DeleteIconButton } from '@/components/delete-icon-button';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { getIssueSeverityBackgroundColor } from '@/helpers/get-status-class';

import '@/helpers/resolutions';

import { ApiError } from '@/lib/api-error';
import { toast, toastError } from '@/lib/toast';
import {
  ManagedResolutionContext,
  ResolutionDisplayItem,
  ResolutionFormValues,
} from './utils';

type ResolutionFormFieldsProps = {
  form: ReturnType<typeof useForm<ResolutionFormValues>>;
  reasonOptions: readonly string[];
};

function ResolutionFormFields({
  form,
  reasonOptions,
}: ResolutionFormFieldsProps) {
  return (
    <>
      <FormField
        control={form.control}
        name='reason'
        render={({ field }) => (
          <FormItem>
            <FormLabel>Reason</FormLabel>
            <Select onValueChange={field.onChange} value={field.value}>
              <FormControl>
                <SelectTrigger>
                  <SelectValue placeholder='Select a reason' />
                </SelectTrigger>
              </FormControl>
              <SelectContent>
                {reasonOptions.map((reason) => (
                  <SelectItem key={reason} value={reason}>
                    {reason}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <FormMessage />
          </FormItem>
        )}
      />
      <FormField
        control={form.control}
        name='comment'
        render={({ field }) => (
          <FormItem>
            <FormLabel>Comment</FormLabel>
            <FormControl>
              <Input {...field} placeholder='Add a comment...' />
            </FormControl>
            <FormMessage />
          </FormItem>
        )}
      />
    </>
  );
}

type ResolutionFormProps = {
  context: ManagedResolutionContext;
  mode: 'create' | 'edit';
  defaultValues: ResolutionFormValues;
  onCancel?: () => void;
  onSuccess?: () => void;
};

export function ResolutionForm({
  context,
  mode,
  defaultValues,
  onCancel,
  onSuccess,
}: ResolutionFormProps) {
  const queryClient = useQueryClient();
  const isIssue = context.itemType === 'issue';
  const reasonOptions = isIssue
    ? zIssueResolutionReason.options
    : zVulnerabilityResolutionReason.options;
  const schema = isIssue
    ? mode === 'create'
      ? zPostIssueResolution.omit({ message: true })
      : zPatchIssueResolution
    : mode === 'create'
      ? zPostVulnerabilityResolution
      : zPatchVulnerabilityResolution;

  const form = useForm<ResolutionFormValues>({
    resolver: zodResolver(schema) as Resolver<ResolutionFormValues>,
    defaultValues,
  });

  const issueCreateMutation = useMutation({
    ...postIssueResolutionForRepositoryMutation(),
    onSuccess() {
      queryClient.invalidateQueries({
        queryKey: getRunIssuesQueryKey({
          path: { runId: context.runId },
        }),
      });
      toast.info(
        mode === 'create' ? 'Resolution created' : 'Resolution updated',
        {
          description:
            mode === 'create'
              ? 'Issue resolution created successfully.'
              : 'Issue resolution updated successfully.',
        }
      );
    },
    onError(error: ApiError) {
      toastError(error.message, error);
    },
  });
  const issueUpdateMutation = useMutation({
    ...patchIssueResolutionForRepositoryMutation(),
    onSuccess() {
      queryClient.invalidateQueries({
        queryKey: getRunIssuesQueryKey({
          path: { runId: context.runId },
        }),
      });
      toast.info('Resolution updated', {
        description: 'Issue resolution updated successfully.',
      });
    },
    onError(error: ApiError) {
      toastError(error.message, error);
    },
  });

  const vulnerabilityCreateMutation = useMutation({
    ...postVulnerabilityResolutionMutation(),
    onSuccess() {
      queryClient.invalidateQueries({
        queryKey: getRunVulnerabilitiesQueryKey({
          path: { runId: context.runId },
        }),
      });
      toast.info(
        mode === 'create' ? 'Resolution created' : 'Resolution updated',
        {
          description:
            mode === 'create'
              ? 'Vulnerability resolution created successfully.'
              : 'Vulnerability resolution updated successfully.',
        }
      );
    },
    onError(error: ApiError) {
      toastError(error.message, error);
    },
  });
  const vulnerabilityUpdateMutation = useMutation({
    ...patchVulnerabilityResolutionMutation(),
    onSuccess() {
      queryClient.invalidateQueries({
        queryKey: getRunVulnerabilitiesQueryKey({
          path: { runId: context.runId },
        }),
      });
      toast.info('Resolution updated', {
        description: 'Vulnerability resolution updated successfully.',
      });
    },
    onError(error: ApiError) {
      toastError(error.message, error);
    },
  });

  const isPending = isIssue
    ? mode === 'create'
      ? issueCreateMutation.isPending
      : issueUpdateMutation.isPending
    : mode === 'create'
      ? vulnerabilityCreateMutation.isPending
      : vulnerabilityUpdateMutation.isPending;

  const onSubmit = async (values: ResolutionFormValues) => {
    if (isIssue) {
      if (mode === 'create') {
        await issueCreateMutation.mutateAsync({
          path: {
            repositoryId: context.repositoryId,
          },
          body: {
            comment: values.comment,
            message: context.identifier,
            reason: values.reason as IssueResolutionReason,
          } satisfies PostIssueResolution,
        });
      } else {
        await issueUpdateMutation.mutateAsync({
          path: {
            repositoryId: context.repositoryId,
            messageHash: context.identifier,
          },
          body: {
            comment: values.comment,
            reason: values.reason as IssueResolutionReason,
          } satisfies PatchIssueResolution,
        });
      }
    } else {
      if (mode === 'create') {
        await vulnerabilityCreateMutation.mutateAsync({
          path: {
            repositoryId: context.repositoryId,
            externalId: context.identifier,
          },
          body: values as PostVulnerabilityResolution,
        });
      } else {
        await vulnerabilityUpdateMutation.mutateAsync({
          path: {
            repositoryId: context.repositoryId,
            externalId: context.identifier,
          },
          body: {
            comment: values.comment,
            reason: values.reason as VulnerabilityResolutionReason,
          } satisfies PatchVulnerabilityResolution,
        });
      }
    }

    if (mode === 'edit') {
      onSuccess?.();
    }
  };

  const formButtons =
    mode === 'create' ? (
      <Button type='submit' disabled={isPending} className='w-fit'>
        {isPending ? 'Creating...' : 'Create resolution'}
      </Button>
    ) : (
      <div className='flex gap-2'>
        <Button type='submit' disabled={isPending}>
          {isPending ? 'Saving...' : 'Save'}
        </Button>
        <Button type='button' variant='outline' onClick={onCancel}>
          Cancel
        </Button>
      </div>
    );

  const formContent = (
    <Form {...form}>
      <form
        onSubmit={form.handleSubmit(onSubmit)}
        className='flex flex-col gap-4'
      >
        <ResolutionFormFields form={form} reasonOptions={reasonOptions} />
        {formButtons}
      </form>
    </Form>
  );

  if (mode === 'create') {
    return (
      <Card>
        <CardContent>{formContent}</CardContent>
      </Card>
    );
  }

  return <CardContent>{formContent}</CardContent>;
}
type ResolutionCardProps = {
  displayItem: ResolutionDisplayItem;
  editingKey: string | null;
  setEditingKey: (key: string | null) => void;
  context?: ManagedResolutionContext;
  onDelete?: () => void;
};

export function ResolutionCard({
  displayItem,
  editingKey,
  setEditingKey,
  context,
  onDelete,
}: ResolutionCardProps) {
  const { key, resolution, state, showActions } = displayItem;
  const isEditing = editingKey === key;
  const pendingLabel =
    state === 'pending-create'
      ? 'Created: pending rerun'
      : state === 'pending-update'
        ? 'Updated: pending rerun'
        : state === 'pending-delete'
          ? 'Deleted: pending rerun'
          : null;

  return (
    <Card key={key}>
      <CardHeader>
        <CardTitle className='flex items-start justify-between gap-2'>
          <div className='flex min-w-0 items-center gap-2'>
            <span
              className={
                state === 'pending-delete'
                  ? 'text-muted-foreground line-through'
                  : undefined
              }
            >
              {resolution.reason}
            </span>
            {pendingLabel && (
              <Tooltip>
                <TooltipTrigger>
                  <Badge variant='small' className='bg-yellow-200 text-black'>
                    {pendingLabel}
                  </Badge>
                </TooltipTrigger>
                <TooltipContent>
                  The operation will be applied on the next run.
                </TooltipContent>
              </Tooltip>
            )}
          </div>
          <Badge
            variant='small'
            className={getIssueSeverityBackgroundColor('HINT')}
          >
            {resolution.source}
          </Badge>
        </CardTitle>
      </CardHeader>
      {isEditing && context ? (
        <ResolutionForm
          context={context}
          mode='edit'
          defaultValues={{
            comment: resolution.comment,
            reason: resolution.reason as ResolutionFormValues['reason'],
          }}
          onCancel={() => setEditingKey(null)}
          onSuccess={() => setEditingKey(null)}
        />
      ) : (
        <CardContent className='flex items-start justify-between gap-2'>
          <div className='min-w-0 flex-1 flex-col gap-2'>
            <div className='italic'>{resolution.comment}</div>
            <div className='flex items-start gap-2'>
              <div className='text-muted-foreground shrink-0 font-semibold'>
                {'externalId' in resolution
                  ? 'ID Matcher:'
                  : 'Message Matcher:'}
              </div>
              <div className='text-muted-foreground flex-1 break-all whitespace-pre-wrap'>
                {'externalId' in resolution
                  ? resolution.externalId
                  : resolution.message}
              </div>
            </div>
          </div>
          {showActions && context && onDelete && (
            <div className='flex flex-col gap-1'>
              <Button
                variant='outline'
                size='sm'
                onClick={() => setEditingKey(key)}
              >
                <Pencil className='h-4 w-4' />
              </Button>
              <DeleteDialog
                thingName='resolution'
                uiComponent={<DeleteIconButton />}
                onDelete={onDelete}
              />
            </div>
          )}
        </CardContent>
      )}
    </Card>
  );
}
