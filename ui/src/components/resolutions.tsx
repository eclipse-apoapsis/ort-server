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

import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Pencil } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import {
  deleteVulnerabilityResolutionMutation,
  getRunVulnerabilitiesQueryKey,
  patchVulnerabilityResolutionMutation,
  postVulnerabilityResolutionMutation,
} from '@/api/@tanstack/react-query.gen';
import {
  AppliedVulnerabilityResolution,
  VulnerabilityResolution,
} from '@/api/types.gen';
import {
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
import {
  getAppliedVulnerabilityResolutions,
  getUnappliedVulnerabilityResolutions,
  hasVulnerabilityResolutionActivity,
  isVulnerabilityItem,
  ItemWithResolutions,
} from '@/helpers/resolutions';
import { ApiError } from '@/lib/api-error';
import { toast, toastError } from '@/lib/toast';

type ResolutionFormValues = z.infer<typeof zPostVulnerabilityResolution>;
type ResolutionItem =
  | NonNullable<ItemWithResolutions['resolutions']>[number]
  | VulnerabilityResolution;

type ResolutionFormFieldsProps = {
  form: ReturnType<typeof useForm<ResolutionFormValues>>;
};

function ResolutionFormFields({ form }: ResolutionFormFieldsProps) {
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
                {zVulnerabilityResolutionReason.options.map((reason) => (
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
              <Input {...field} placeholder='(optional)' />
            </FormControl>
            <FormMessage />
          </FormItem>
        )}
      />
    </>
  );
}

type ManagedResolutionContext = {
  repositoryId: string;
  externalId: string;
  runId: number;
};

type ResolutionFormProps = {
  context: ManagedResolutionContext;
  mode: 'create' | 'edit';
  defaultValues: ResolutionFormValues;
  onCancel?: () => void;
  onSuccess?: () => void;
};

function ResolutionForm({
  context,
  mode,
  defaultValues,
  onCancel,
  onSuccess,
}: ResolutionFormProps) {
  const queryClient = useQueryClient();

  const form = useForm<ResolutionFormValues>({
    resolver: zodResolver(zPostVulnerabilityResolution),
    defaultValues,
  });

  const { mutateAsync, isPending } = useMutation({
    ...(mode === 'create'
      ? postVulnerabilityResolutionMutation()
      : patchVulnerabilityResolutionMutation()),
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

  const onSubmit = async (values: ResolutionFormValues) => {
    await mutateAsync({
      path: {
        repositoryId: context.repositoryId,
        externalId: context.externalId,
      },
      body: values,
    });

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
        <ResolutionFormFields form={form} />
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

type ResolutionDisplayItem = {
  key: string;
  resolution: ResolutionItem;
  state: 'applied' | 'pending-create' | 'pending-update' | 'pending-delete';
  showActions: boolean;
};

function getResolutionKey(
  resolution:
    | Pick<AppliedVulnerabilityResolution, 'source'>
    | Pick<VulnerabilityResolution, 'source'>
) {
  return resolution.source;
}

function getVulnerabilityDisplayItems(
  item: Extract<ItemWithResolutions, { vulnerability: unknown }>,
  canManage: boolean
): ResolutionDisplayItem[] {
  const appliedResolutions = getAppliedVulnerabilityResolutions(item);
  const unappliedResolutions = getUnappliedVulnerabilityResolutions(item);
  const unappliedBySource = new Map(
    unappliedResolutions.map((resolution) => [
      getResolutionKey(resolution),
      resolution,
    ])
  );

  const displayItems: ResolutionDisplayItem[] = [];

  for (const resolution of appliedResolutions) {
    const source = getResolutionKey(resolution);
    const unappliedResolution = unappliedBySource.get(source);

    displayItems.push({
      key: `${source}:applied`,
      resolution,
      state:
        resolution.isDeleted && !unappliedResolution
          ? 'pending-delete'
          : 'applied',
      showActions: !resolution.isDeleted && !unappliedResolution && canManage,
    });

    if (unappliedResolution) {
      displayItems.push({
        key: `${source}:pending-update`,
        resolution: unappliedResolution,
        state: 'pending-update',
        showActions: canManage,
      });
    }
  }

  for (const resolution of unappliedResolutions) {
    if (
      !appliedResolutions.some(
        (applied) => getResolutionKey(applied) === getResolutionKey(resolution)
      )
    ) {
      displayItems.push({
        key: `${getResolutionKey(resolution)}:pending-create`,
        resolution,
        state: 'pending-create',
        showActions: canManage,
      });
    }
  }

  return displayItems;
}

function getDisplayItems(
  item: ItemWithResolutions,
  canManage: boolean
): ResolutionDisplayItem[] {
  if (isVulnerabilityItem(item)) {
    return getVulnerabilityDisplayItems(item, canManage);
  }

  return (item.resolutions ?? []).map((resolution) => ({
    key: resolution.source,
    resolution,
    state: 'applied',
    showActions: false,
  }));
}

type ResolutionsProps = {
  item: ItemWithResolutions;
  repositoryId?: string;
  runId?: number;
};

type ResolutionCardProps = {
  displayItem: ResolutionDisplayItem;
  editingKey: string | null;
  setEditingKey: (key: string | null) => void;
  context?: ManagedResolutionContext;
  onDelete?: () => void;
};

function ResolutionCard({
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
      ? 'Created: Pending rerun'
      : state === 'pending-update'
        ? 'Updated: Pending rerun'
        : state === 'pending-delete'
          ? 'Deleted: Pending rerun'
          : null;

  return (
    <Card key={key}>
      <CardHeader>
        <CardTitle className='flex justify-between'>
          <div className='flex items-center gap-2'>
            {resolution.reason}
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
          <div className='flex flex-col gap-2'>
            <div className='italic'>{resolution.comment}</div>
            <div className='flex gap-2'>
              <div className='text-muted-foreground font-semibold'>
                {'externalId' in resolution
                  ? 'ID Matcher:'
                  : 'Message Matcher:'}
              </div>
              <div className='text-muted-foreground'>
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

function VulnerabilityResolutions({
  item,
  repositoryId,
  runId,
}: {
  item: Extract<ItemWithResolutions, { vulnerability: unknown }>;
  repositoryId: string;
  runId: number;
}) {
  const [editingKey, setEditingKey] = useState<string | null>(null);
  const queryClient = useQueryClient();
  const context: ManagedResolutionContext = {
    repositoryId,
    externalId: item.vulnerability.externalId,
    runId,
  };
  const displayItems = getDisplayItems(item, true);
  const hasAnyResolution = hasVulnerabilityResolutionActivity(item);

  const { mutateAsync: deleteResolution } = useMutation({
    ...deleteVulnerabilityResolutionMutation(),
    onSuccess() {
      queryClient.invalidateQueries({
        queryKey: getRunVulnerabilitiesQueryKey({ path: { runId } }),
      });
      toast.info('Resolution deleted', {
        description: 'Vulnerability resolution deleted successfully.',
      });
    },
    onError(error: ApiError) {
      toastError(error.message, error);
    },
  });

  return (
    <div className='flex flex-col gap-2'>
      {displayItems.map((displayItem) => (
        <ResolutionCard
          key={displayItem.key}
          displayItem={displayItem}
          editingKey={editingKey}
          setEditingKey={setEditingKey}
          context={context}
          onDelete={() =>
            deleteResolution({
              path: {
                repositoryId: context.repositoryId,
                externalId: context.externalId,
              },
            })
          }
        />
      ))}
      {!hasAnyResolution && (
        <ResolutionForm
          context={context}
          mode='create'
          defaultValues={{
            comment: '',
            reason: 'CANT_FIX_VULNERABILITY',
          }}
        />
      )}
    </div>
  );
}

export function Resolutions({ item, repositoryId, runId }: ResolutionsProps) {
  const [editingKey, setEditingKey] = useState<string | null>(null);
  const displayItems = getDisplayItems(item, false);

  if ('vulnerability' in item && repositoryId && runId !== undefined) {
    return (
      <VulnerabilityResolutions
        item={item}
        repositoryId={repositoryId}
        runId={runId}
      />
    );
  }

  return (
    <div className='flex flex-col gap-2'>
      {displayItems.map((displayItem) => (
        <ResolutionCard
          key={displayItem.key}
          displayItem={displayItem}
          editingKey={editingKey}
          setEditingKey={setEditingKey}
        />
      ))}
    </div>
  );
}
