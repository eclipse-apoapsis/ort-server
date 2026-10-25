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

import { Archive, Clock, HardDrive, RefreshCw, Trophy } from 'lucide-react';

import { ProvenanceFile, ProvenanceFileStatistics } from '@/api';
import { StatisticsCard } from '@/components/statistics-card';
import { TimestampWithUTC } from '@/components/timestamp-with-utc';
import { TooltipIfTruncated } from '@/components/tooltip-if-truncated';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { formatFileSize } from '@/helpers/format-file-size';
import { cn } from '@/lib/utils';
import { provenanceFileKindLabels } from './utils';

type ProvenanceFileStatisticsViewProps = {
  /** The statistics to display. */
  statistics: ProvenanceFileStatistics;
  /** Request the server to list the storage again. */
  onRefresh: () => void;
  /** Whether a refresh is currently in progress. */
  isRefreshing: boolean;
  /** Filter the table by the given URL. */
  onFilter: (url: string) => void;
};

const kindColors: Record<ProvenanceFile['kind'], string> = {
  SOURCE_ARTIFACT: 'bg-blue-500',
  VCS: 'bg-green-500',
  UNKNOWN: 'bg-gray-400',
};

/** Shorten the given [text] to at most [maxLength] characters by replacing its middle part with an ellipsis. */
const truncateMiddle = (text: string, maxLength = 48) => {
  if (text.length <= maxLength) return text;

  const half = Math.floor((maxLength - 1) / 2);
  return `${text.slice(0, half)}…${text.slice(text.length - half)}`;
};

/**
 * Show statistics about all stored provenance files of a type, such as file archives or file lists.
 */
export const ProvenanceFileStatisticsView = ({
  statistics,
  onRefresh,
  isRefreshing,
  onFilter,
}: ProvenanceFileStatisticsViewProps) => {
  const kindSummary = statistics.byKind
    .map(
      ({ kind, count, sizeInBytes }) =>
        `${provenanceFileKindLabels[kind]}: ${count} (${formatFileSize(sizeInBytes)})`
    )
    .join(', ');

  return (
    <div className='flex flex-col gap-4'>
      <div className='text-muted-foreground flex items-center justify-between gap-2 text-sm'>
        <span>
          Statistics as of{' '}
          <TimestampWithUTC
            timestamp={statistics.snapshotTime}
            label='Time of the storage listing'
          />
          . The listing is cached for a few minutes.
        </span>
        <Button
          size='sm'
          variant='outline'
          onClick={onRefresh}
          disabled={isRefreshing}
        >
          <RefreshCw
            className={cn('h-4 w-4', isRefreshing && 'animate-spin')}
          />
          Refresh
        </Button>
      </div>
      <div className='grid grid-cols-1 gap-4 md:grid-cols-3 xl:grid-cols-5'>
        <StatisticsCard
          title='Files'
          icon={Archive}
          value={statistics.totalCount}
          counts={statistics.byKind.map(({ kind, count }) => ({
            key: provenanceFileKindLabels[kind],
            count,
            color: kindColors[kind],
          }))}
          description={kindSummary || 'No files stored'}
        />
        <StatisticsCard
          title='Total size'
          icon={HardDrive}
          value={formatFileSize(statistics.totalSizeInBytes)}
          description={`Average size: ${formatFileSize(statistics.averageSizeInBytes)}`}
        />
        <StatisticsCard
          title='Largest file'
          icon={Trophy}
          value={
            statistics.largest
              ? formatFileSize(statistics.largest.sizeInBytes)
              : '-'
          }
          description={truncateMiddle(
            statistics.largest?.url ?? statistics.largest?.key ?? ''
          )}
        />
        <StatisticsCard
          title='Newest file'
          icon={Clock}
          value={
            statistics.newestLastModified ? (
              <TimestampWithUTC timestamp={statistics.newestLastModified} />
            ) : (
              '-'
            )
          }
          description='The time of the most recent modification'
        />
        <StatisticsCard
          title='Oldest file'
          icon={Clock}
          value={
            statistics.oldestLastModified ? (
              <TimestampWithUTC timestamp={statistics.oldestLastModified} />
            ) : (
              '-'
            )
          }
          description='The time of the least recent modification'
        />
      </div>
      {statistics.topUrls.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className='text-sm font-semibold'>
              Largest URLs
            </CardTitle>
            <CardDescription>
              The source artifacts and repositories with the largest total size
              of stored files. Many stored revisions of the same repository are
              a common reason for a growing storage. Click a URL to filter the
              table.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <table className='w-full text-sm'>
              <thead>
                <tr className='text-muted-foreground text-left'>
                  <th className='font-medium'>URL</th>
                  <th className='w-24 text-right font-medium'>Files</th>
                  <th className='w-28 text-right font-medium'>Size</th>
                </tr>
              </thead>
              <tbody>
                {statistics.topUrls.map(({ url, count, sizeInBytes }) => (
                  <tr key={url}>
                    <td className='max-w-0 py-1 pr-2'>
                      <Button
                        variant='link'
                        className='h-auto w-full justify-start p-0'
                        onClick={() => onFilter(url)}
                      >
                        <TooltipIfTruncated text={url} />
                      </Button>
                    </td>
                    <td className='text-right'>{count}</td>
                    <td className='text-right'>
                      {formatFileSize(sizeInBytes)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </CardContent>
        </Card>
      )}
    </div>
  );
};
