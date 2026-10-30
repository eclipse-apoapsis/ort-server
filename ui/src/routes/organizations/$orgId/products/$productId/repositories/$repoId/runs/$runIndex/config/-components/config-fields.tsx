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

import { InfoIcon } from 'lucide-react';
import { createContext, ReactNode, useContext, useState } from 'react';

import { PluginType } from '@/api';
import { MarkdownRenderer } from '@/components/markdown-renderer';
import { Badge } from '@/components/ui/badge';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { cn } from '@/lib/utils';
import { reconstructScannerSelection } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/create-run/-components/plugin-utils';
import {
  ConfigPath,
  isLeafValue,
  isRecord,
  pluginCollections,
  PluginDescriptorsContext,
  sortEntries,
  useListEntryName,
} from './config-utils';
import { ConfigValue, NoneValue } from './config-value';

/**
 * The scanners of a run, from both lists that name them: `scanners` runs on packages,
 * `projectScanners` on projects. The create-run form shows them as one list with the scope of
 * each scanner, and so does this page.
 */
type ScannerList = {
  kind: typeof SCANNERS;
  packages: string[];
  /** Unset if the package scanners scan projects too. */
  projects: string[] | null;
};

const SCANNERS = Symbol('scanners');

const isScannerList = (value: unknown): value is ScannerList =>
  isRecord(value) && value.kind === SCANNERS;

const toStrings = (value: unknown) =>
  Array.isArray(value)
    ? value.filter((entry): entry is string => typeof entry === 'string')
    : [];

const toProjectScanners = (value: unknown) =>
  value === undefined || value === null ? null : toStrings(value);

const scopeLabels = {
  both: 'Both',
  packages: 'Packages only',
  projects: 'Projects only',
} as const;

/**
 * The scanners with their scope, worded as in the scope toggle of the create-run form and worked
 * out as the form does: without project scanners, the package scanners scan projects too.
 */
const scannerScopes = (packages: string[], projects: string[] | null) => {
  const selection = reconstructScannerSelection(packages, projects, {
    scanners: [],
    scannerScopes: {},
  });

  return selection.scanners.map((id) => ({
    id,
    scope: scopeLabels[selection.scannerScopes[id] ?? 'packages'],
  }));
};

const ScannersRow = ({
  label,
  description,
  value,
}: {
  label: string;
  description?: ReactNode;
  value: ScannerList;
}) => {
  const scannerName = useListEntryName(['scanner', 'scanners']);
  const scanners = scannerScopes(value.packages, value.projects);

  return (
    <div className={rowClassName}>
      <Term label={label} description={description} />
      <dd className='min-w-0 text-sm'>
        {scanners.length === 0 ? (
          <NoneValue />
        ) : (
          <ul className='flex flex-wrap gap-1.5'>
            {scanners.map(({ id, scope }) => (
              <li key={id}>
                <Badge variant='secondary' className='font-normal'>
                  {scannerName(id)}{' '}
                  <span className='text-muted-foreground'>{scope}</span>
                </Badge>
              </li>
            ))}
          </ul>
        )}
      </dd>
    </div>
  );
};

/**
 * An icon next to a label that shows the help text of the setting on hover, on keyboard focus
 * and on tap.
 */
const HelpTip = ({
  label,
  children,
}: {
  label: string;
  children: ReactNode;
}) => {
  const [open, setOpen] = useState(false);

  return (
    <Tooltip open={open} onOpenChange={setOpen}>
      <TooltipTrigger asChild>
        <button
          type='button'
          aria-label={`About ${label}`}
          className='text-muted-foreground hover:text-foreground focus-visible:ring-ring/50 inline-flex size-5 shrink-0 items-center justify-center rounded-full outline-none focus-visible:ring-2'
          onClick={(event) => {
            // The trigger closes the tooltip on click; keep it open instead, so that a tap on a
            // touch screen, which does not hover, shows it too.
            event.preventDefault();
            setOpen(true);
          }}
        >
          <InfoIcon aria-hidden className='size-3.5' />
        </button>
      </TooltipTrigger>
      <TooltipContent className='max-w-xs text-left'>{children}</TooltipContent>
    </Tooltip>
  );
};

const Term = ({
  label,
  description,
}: {
  label: string;
  description?: ReactNode;
}) => (
  <dt className='text-muted-foreground flex flex-wrap content-start items-center gap-x-1 gap-y-1 self-start text-sm font-medium'>
    <span className='break-words'>{label}</span>
    {description && <HelpTip label={label}>{description}</HelpTip>}
  </dt>
);

/** Whether a list sits inside a block, where its label column is only as wide as its labels. */
const InsideBlockContext = createContext(false);

/**
 * The list that settings are laid out in: one setting per line, the label in a column of its
 * own and the value next to it, or below the label where the list is narrow.
 */
export const ConfigList = ({
  children,
  className,
}: {
  children: ReactNode;
  className?: string;
}) => {
  const insideBlock = useContext(InsideBlockContext);

  return (
    <div className={cn('@container', className)}>
      <dl
        className={cn(
          'grid grid-cols-1 divide-y',
          insideBlock
            ? 'gap-x-4 @3xs:grid-cols-[fit-content(50%)_minmax(0,1fr)]'
            : 'gap-x-6 @lg:grid-cols-[15rem_minmax(0,1fr)]'
        )}
      >
        {children}
      </dl>
    </div>
  );
};

const rowClassName =
  'col-span-full grid grid-cols-subgrid gap-y-1 py-2.5 first:pt-0 last:pb-0';

type RowProps = {
  label: string;
  description?: ReactNode;
  children: ReactNode;
};

/**
 * A setting shown as its label and its value.
 */
export const ConfigRow = ({ label, description, children }: RowProps) => (
  <div className={rowClassName}>
    <Term label={label} description={description} />
    <dd className='min-w-0 text-sm break-words'>{children}</dd>
  </div>
);

/**
 * A setting that holds further settings, shown in the value column.
 */
export const ConfigGroup = ({ label, description, children }: RowProps) => (
  <div className={rowClassName}>
    <Term label={label} description={description} />
    <dd className='min-w-0'>{children}</dd>
  </div>
);

/** Plugin blocks and list entries side by side, as many as fit. */
const BlockGrid = ({ children }: { children: ReactNode }) => (
  <div className='@container'>
    <div className='grid grid-cols-1 gap-3 @xl:grid-cols-2 @4xl:grid-cols-3'>
      {children}
    </div>
  </div>
);

const ConfigBlock = ({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle?: string;
  children: ReactNode;
}) => (
  <section className='rounded-md border p-3'>
    <h4 className='mb-3 text-sm font-semibold'>
      {title}
      {subtitle && (
        <span className='text-muted-foreground ml-2 font-mono text-xs font-normal'>
          {subtitle}
        </span>
      )}
    </h4>
    <InsideBlockContext.Provider value={true}>
      {children}
    </InsideBlockContext.Provider>
  </section>
);
type PluginBlockProps = {
  pluginType: PluginType;
  pluginId: string;
  instanceId?: string;
  value: Record<string, unknown>;
  path: ConfigPath;
};

/**
 * The settings of one plugin. Its options are named and explained from the plugin descriptor,
 * as on the create-run form.
 */
const PluginBlock = ({
  pluginType,
  pluginId,
  instanceId,
  value,
  path,
}: PluginBlockProps) => {
  const plugins = useContext(PluginDescriptorsContext);
  const descriptor = plugins.find(
    (plugin) => plugin.type === pluginType && plugin.id === pluginId
  );
  const { options, secrets } = value;
  const rest = Object.fromEntries(
    Object.entries(value).filter(
      ([key]) => !['type', 'id', 'options', 'secrets'].includes(key)
    )
  );

  const optionRows = (['options', 'secrets'] as const).flatMap((section) => {
    const entries = section === 'options' ? options : secrets;

    if (!isRecord(entries)) return [];

    return Object.entries(entries).map(([name, optionValue]) => {
      const optionIndex =
        descriptor?.options.findIndex((option) => option.name === name) ?? -1;
      return {
        name,
        optionValue,
        path: [...path, section, name],
        description: descriptor?.options[optionIndex]?.description,
        position: optionIndex === -1 ? Number.POSITIVE_INFINITY : optionIndex,
      };
    });
  });

  optionRows.sort(
    (first, second) =>
      first.position - second.position || first.name.localeCompare(second.name)
  );

  const hasSettings = Object.keys(rest).length > 0 || optionRows.length > 0;

  return (
    <ConfigBlock
      title={descriptor?.displayName ?? pluginId}
      subtitle={instanceId && instanceId !== pluginId ? instanceId : undefined}
    >
      {hasSettings ? (
        <ConfigList>
          {sortEntries(Object.entries(rest), path).map((entry) => (
            <ConfigEntry
              key={entry.key}
              value={entry.value}
              path={[...path, entry.key]}
              label={entry.label}
              description={entry.description}
            />
          ))}
          {optionRows.map((row) => (
            <ConfigRow
              key={row.path.join('.')}
              label={row.name}
              description={
                row.description && (
                  <MarkdownRenderer
                    markdown={row.description}
                    className='max-w-none text-xs text-inherit [&_*]:text-inherit [&_p]:my-0'
                  />
                )
              }
            >
              <ConfigValue value={row.optionValue} path={row.path} />
            </ConfigRow>
          ))}
        </ConfigList>
      ) : (
        <p className='text-muted-foreground text-sm italic'>No settings</p>
      )}
    </ConfigBlock>
  );
};

const PluginCollection = ({
  pluginType,
  value,
  path,
}: {
  pluginType: PluginType;
  value: unknown;
  path: ConfigPath;
}) => {
  if (Array.isArray(value)) {
    return value
      .filter(isRecord)
      .map((entry, index) => (
        <PluginBlock
          key={index}
          pluginType={pluginType}
          pluginId={String(entry.type)}
          instanceId={typeof entry.id === 'string' ? entry.id : undefined}
          value={entry}
          path={[...path, index]}
        />
      ));
  }

  if (isRecord(value)) {
    return Object.entries(value)
      .filter(([, entry]) => isRecord(entry))
      .map(([pluginId, entry]) => (
        <PluginBlock
          key={pluginId}
          pluginType={pluginType}
          pluginId={pluginId}
          value={entry as Record<string, unknown>}
          path={[...path, pluginId]}
        />
      ));
  }

  return <NoneValue />;
};

type ConfigEntryProps = {
  value: unknown;
  path: ConfigPath;
  label: string;
  description?: ReactNode;
};

const ConfigEntry = ({ value, path, label, description }: ConfigEntryProps) => {
  const pluginType = pluginCollections[path.join('.')];

  if (isScannerList(value)) {
    return (
      <ScannersRow label={label} description={description} value={value} />
    );
  }

  if (pluginType && !isLeafValue(value)) {
    return (
      <ConfigGroup label={label} description={description}>
        <BlockGrid>
          <PluginCollection pluginType={pluginType} value={value} path={path} />
        </BlockGrid>
      </ConfigGroup>
    );
  }

  if (isLeafValue(value) || (!Array.isArray(value) && !isRecord(value))) {
    return (
      <ConfigRow label={label} description={description}>
        <ConfigValue value={value} path={path} />
      </ConfigRow>
    );
  }

  if (Array.isArray(value)) {
    return (
      <ConfigGroup label={label} description={description}>
        <BlockGrid>
          {value.map((entry, index) => (
            <ConfigBlock
              key={index}
              title={
                isRecord(entry) && typeof entry.name === 'string'
                  ? entry.name
                  : `#${index + 1}`
              }
            >
              {isRecord(entry) ? (
                <ConfigFields value={entry} path={[...path, index]} />
              ) : (
                <ConfigValue value={entry} path={[...path, index]} />
              )}
            </ConfigBlock>
          ))}
        </BlockGrid>
      </ConfigGroup>
    );
  }

  return (
    <ConfigGroup label={label} description={description}>
      <ConfigFields value={value} path={path} />
    </ConfigGroup>
  );
};

type ConfigFieldsProps = {
  value: Record<string, unknown>;
  path: ConfigPath;
  className?: string;
  /** Keys of settings that have no effect for the run, which are not shown. */
  hiddenKeys?: string[];
};

/**
 * Show every setting of a configuration object, in the order of the shared field table, with
 * settings missing from the table last.
 */
export const ConfigFields = ({
  value,
  path,
  className,
  hiddenKeys = [],
}: ConfigFieldsProps) => {
  const isScanner = path.length === 1 && path[0] === 'scanner';
  const shown = Object.entries(value).filter(
    ([key, entry]) => entry !== undefined && !hiddenKeys.includes(key)
  );
  const entries = sortEntries(
    isScanner
      ? [
          ...shown.filter(
            ([key]) => key !== 'scanners' && key !== 'projectScanners'
          ),
          [
            'scanners',
            {
              kind: SCANNERS,
              packages: toStrings(value.scanners),
              projects: toProjectScanners(value.projectScanners),
            } satisfies ScannerList,
          ],
        ]
      : shown,
    path
  );

  if (entries.length === 0) {
    return <NoneValue />;
  }

  return (
    <ConfigList className={className}>
      {entries.map((entry) => (
        <ConfigEntry
          key={entry.key}
          value={entry.value}
          path={[...path, entry.key]}
          label={entry.label}
          description={entry.description}
        />
      ))}
    </ConfigList>
  );
};
