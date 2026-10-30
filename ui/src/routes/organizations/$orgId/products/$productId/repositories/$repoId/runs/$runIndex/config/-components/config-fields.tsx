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

import { ArrowRightIcon, InfoIcon, ServerIcon } from 'lucide-react';
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
import { formatRunConfigurationDiffValue } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/-components/run-configuration-diff-dialog-utils';
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
import {
  findChange,
  removedEntries,
  removedKeys,
  ServerChange,
  useServerChange,
  useServerChanges,
} from './server-changes';

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

const scannersPath = ['scanner', 'scanners'];
const projectScannersPath = ['scanner', 'projectScanners'];

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
  const changes = useServerChanges();
  const scannerName = useListEntryName(scannersPath);
  const formatScanner = ({ id, scope }: { id: string; scope: string }) =>
    `${scannerName(id)} (${scope})`;

  /** The requested value of a list: the resolved one unless the server changed it. */
  const requestedList = (path: ConfigPath, resolved: string[] | null) => {
    const change = findChange(changes, path);

    return change ? toProjectScanners(change.requested) : resolved;
  };

  const scanners = scannerScopes(value.packages, value.projects);
  const requested = scannerScopes(
    requestedList(scannersPath, value.packages) ?? [],
    requestedList(projectScannersPath, value.projects)
  );
  const requestedScope = (id: string) =>
    requested.find((scanner) => scanner.id === id)?.scope;
  const rescoped = scanners.filter(
    ({ id, scope }) => requestedScope(id) && requestedScope(id) !== scope
  );

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
        {requested.length === 0 && scanners.length > 0 ? (
          <ChangeLine kind='added' />
        ) : (
          <ListChanges
            added={scanners
              .filter(({ id }) => !requestedScope(id))
              .map(formatScanner)}
            removed={requested
              .filter(
                ({ id }) => !scanners.some((scanner) => scanner.id === id)
              )
              .map(formatScanner)}
          />
        )}
        {rescoped.length > 0 && (
          <ChangeLine kind='changed'>
            {rescoped
              .map(
                ({ id, scope }) =>
                  `${scannerName(id)}: ${requestedScope(id)} → ${scope}`
              )
              .join(', ')}
          </ChangeLine>
        )}
      </dd>
    </div>
  );
};

const changeLabels: Record<ServerChange['kind'], string> = {
  added: 'Added by the server',
  changed: 'Changed by the server',
  removed: 'Removed by the server',
};

/** Says what the server did. This is routine, for example a template of an administrator. */
export const ServerChangeLabel = ({ kind }: { kind: ServerChange['kind'] }) => (
  <span className='text-muted-foreground inline-flex items-center gap-1 text-xs'>
    <ServerIcon aria-hidden className='size-3 shrink-0' />
    {changeLabels[kind]}
  </span>
);

/** A value of a change, in the same form as the setting shows it where it fits in one row. */
const ChangeValue = ({ value, path }: { value: unknown; path: ConfigPath }) =>
  isLeafValue(value) ? (
    <ConfigValue value={value} path={path} />
  ) : (
    <code className='font-mono break-words'>
      {formatRunConfigurationDiffValue(value)}
    </code>
  );

/** A value that was requested and that the run did not use. */
const DroppedValue = ({ children }: { children: ReactNode }) => (
  <del className='text-muted-foreground min-w-0'>{children}</del>
);

const ChangeLine = ({
  kind,
  children,
}: {
  kind: ServerChange['kind'];
  children?: ReactNode;
}) => (
  <div className='mt-1 flex flex-wrap items-center gap-x-2 gap-y-1'>
    <ServerChangeLabel kind={kind} />
    {children}
  </div>
);

/**
 * A list of settings objects that the server put in another order, named by the entries in the
 * order before and after.
 */
const ListOrderChange = ({
  path,
  requested,
  resolved,
}: {
  path: ConfigPath;
  requested: unknown[];
  resolved: unknown[];
}) => {
  const plugins = useContext(PluginDescriptorsContext);
  const pluginType = pluginCollections[path.join('.')];

  const name = (entry: unknown, index: number) => {
    if (!pluginType || !isRecord(entry)) return entryTitle(entry, index);

    const pluginId = String(entry.type);
    const pluginName =
      plugins.find(
        (plugin) => plugin.type === pluginType && plugin.id === pluginId
      )?.displayName ?? pluginId;

    return typeof entry.id === 'string' && entry.id !== pluginId
      ? `${pluginName} (${entry.id})`
      : pluginName;
  };

  return (
    <ChangeLine kind='changed'>
      <DroppedValue>{requested.map(name).join(', ')}</DroppedValue>
      <ArrowRightIcon
        aria-hidden
        className='text-muted-foreground size-3.5 shrink-0'
      />
      <span className='sr-only'>now</span>
      <span>{resolved.map(name).join(', ')}</span>
    </ChangeLine>
  );
};

/** The change of a group or a block, whose settings are shown below it. */
const ChangeNote = ({ change }: { change: ServerChange }) => (
  <ChangeLine kind={change.kind}>
    {change.kind !== 'added' && (
      <DroppedValue>
        <ChangeValue value={change.requested} path={change.path} />
      </DroppedValue>
    )}
  </ChangeLine>
);

/** The entries of a list that the server added and removed. */
const ListChanges = ({
  added,
  removed,
}: {
  added: string[];
  removed: string[];
}) => (
  <>
    {added.length > 0 && (
      <ChangeLine kind='added'>{added.join(', ')}</ChangeLine>
    )}
    {removed.length > 0 && (
      <ChangeLine kind='removed'>
        <DroppedValue>{removed.join(', ')}</DroppedValue>
      </ChangeLine>
    )}
  </>
);

const isStringList = (value: unknown): value is string[] =>
  Array.isArray(value) && value.every((entry) => typeof entry === 'string');

/**
 * A value the server replaced. A list that gained or lost entries shows those entries, which is
 * easier to read than the two whole lists. Any other value shows the old and the new value.
 */
const ChangedValue = ({
  change: { requested, resolved, path },
  children,
}: {
  change: Extract<ServerChange, { kind: 'changed' }>;
  children: ReactNode;
}) => {
  const entryName = useListEntryName(path);

  if (isStringList(requested) && isStringList(resolved)) {
    const added = resolved
      .filter((entry) => !requested.includes(entry))
      .map(entryName);
    const removed = requested
      .filter((entry) => !resolved.includes(entry))
      .map(entryName);

    if (added.length > 0 || removed.length > 0) {
      return (
        <>
          {children}
          <ListChanges added={added} removed={removed} />
        </>
      );
    }
  }

  return (
    <>
      <div className='flex flex-wrap items-center gap-1.5'>
        <DroppedValue>
          <ChangeValue value={requested} path={path} />
        </DroppedValue>
        <ArrowRightIcon
          aria-hidden
          className='text-muted-foreground size-3.5 shrink-0'
        />
        <span className='sr-only'>now</span>
        <ChangeValue value={resolved} path={path} />
      </div>
      <ChangeLine kind='changed' />
    </>
  );
};

/** The value of a row, together with what the server did to it. */
const RowValue = ({
  change,
  children,
}: {
  change: ServerChange | undefined;
  children: ReactNode;
}) => {
  switch (change?.kind) {
    case 'added':
      return (
        <>
          {children}
          <ChangeLine kind='added' />
        </>
      );
    case 'changed':
      return <ChangedValue change={change}>{children}</ChangedValue>;
    case 'removed':
      return <ChangeNote change={change} />;
    default:
      return children;
  }
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
  /** The path of the setting, to mark it if the server changed it. */
  path?: ConfigPath;
};

/**
 * A setting shown as its label and its value.
 */
export const ConfigRow = ({ label, description, children, path }: RowProps) => {
  const change = useServerChange(path);

  return (
    <div className={rowClassName}>
      <Term label={label} description={description} />
      <dd className='min-w-0 text-sm break-words'>
        <RowValue change={change}>{children}</RowValue>
      </dd>
    </div>
  );
};

/**
 * A setting that holds further settings, shown in the value column.
 */
export const ConfigGroup = ({
  label,
  description,
  children,
  path,
}: RowProps) => {
  const change = useServerChange(path);

  return (
    <div className={rowClassName}>
      <Term label={label} description={description} />
      <dd className='min-w-0'>
        {change && (
          <div className='mb-2 text-sm'>
            {change.kind === 'changed' &&
            Array.isArray(change.requested) &&
            Array.isArray(change.resolved) ? (
              <ListOrderChange
                path={change.path}
                requested={change.requested}
                resolved={change.resolved}
              />
            ) : (
              <ChangeNote change={change} />
            )}
          </div>
        )}
        {children}
      </dd>
    </div>
  );
};

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
  path,
}: {
  title: string;
  subtitle?: string;
  children?: ReactNode;
  path?: ConfigPath;
}) => {
  const change = useServerChange(path);

  return (
    <section className='rounded-md border p-3'>
      <div className='mb-3'>
        <h4 className='text-sm font-semibold'>
          {title}
          {subtitle && (
            <span className='text-muted-foreground ml-2 font-mono text-xs font-normal'>
              {subtitle}
            </span>
          )}
        </h4>
        {change && (
          <div className='text-sm'>
            <ChangeNote change={change} />
          </div>
        )}
      </div>
      <InsideBlockContext.Provider value={true}>
        {children}
      </InsideBlockContext.Provider>
    </section>
  );
};

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
  const changes = useServerChanges();
  const descriptor = plugins.find(
    (plugin) => plugin.type === pluginType && plugin.id === pluginId
  );
  const blockKeys = ['type', 'id', 'options', 'secrets'];
  const rest = Object.fromEntries(
    Object.entries(value).filter(([key]) => !blockKeys.includes(key))
  );
  const restEntries: [string, unknown][] = [
    ...Object.entries(rest),
    ...removedKeys(changes, path, value)
      .filter((key) => !blockKeys.includes(key))
      .map((key): [string, unknown] => [key, undefined]),
  ];

  const optionRows = (['options', 'secrets'] as const).flatMap((section) => {
    const entries = isRecord(value[section]) ? value[section] : {};
    const sectionPath = [...path, section];

    return [
      ...Object.entries(entries),
      ...removedKeys(changes, sectionPath, entries).map(
        (key): [string, unknown] => [key, undefined]
      ),
    ].map(([name, optionValue]) => {
      const optionIndex =
        descriptor?.options.findIndex((option) => option.name === name) ?? -1;
      return {
        name,
        optionValue,
        path: [...sectionPath, name],
        description: descriptor?.options[optionIndex]?.description,
        position: optionIndex === -1 ? Number.POSITIVE_INFINITY : optionIndex,
      };
    });
  });

  optionRows.sort(
    (first, second) =>
      first.position - second.position || first.name.localeCompare(second.name)
  );

  const hasSettings = restEntries.length > 0 || optionRows.length > 0;

  return (
    <ConfigBlock
      title={descriptor?.displayName ?? pluginId}
      subtitle={instanceId && instanceId !== pluginId ? instanceId : undefined}
      path={path}
    >
      {hasSettings ? (
        <ConfigList>
          {sortEntries(restEntries, path).map((entry) => (
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
              path={row.path}
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
  const changes = useServerChanges();

  if (Array.isArray(value)) {
    return [
      ...value
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
        )),
      ...removedEntries(changes, path).map((entry) => (
        <RemovedPluginBlock
          key={entry.path.join('.')}
          pluginType={pluginType}
          pluginId={
            isRecord(entry.requested) ? String(entry.requested.type) : ''
          }
          path={entry.path}
        />
      )),
    ];
  }

  if (isRecord(value)) {
    return [
      ...Object.entries(value)
        .filter(([, entry]) => isRecord(entry))
        .map(([pluginId, entry]) => (
          <PluginBlock
            key={pluginId}
            pluginType={pluginType}
            pluginId={pluginId}
            value={entry as Record<string, unknown>}
            path={[...path, pluginId]}
          />
        )),
      ...removedKeys(changes, path, value).map((pluginId) => (
        <RemovedPluginBlock
          key={pluginId}
          pluginType={pluginType}
          pluginId={pluginId}
          path={[...path, pluginId]}
        />
      )),
    ];
  }

  return <NoneValue />;
};

const RemovedPluginBlock = ({
  pluginType,
  pluginId,
  path,
}: {
  pluginType: PluginType;
  pluginId: string;
  path: ConfigPath;
}) => {
  const plugins = useContext(PluginDescriptorsContext);
  const descriptor = plugins.find(
    (plugin) => plugin.type === pluginType && plugin.id === pluginId
  );

  return (
    <ConfigBlock title={descriptor?.displayName ?? pluginId} path={path} />
  );
};

/** The title of an entry of a list: its name, or else its position. */
const entryTitle = (entry: unknown, index: number) =>
  isRecord(entry) && typeof entry.name === 'string'
    ? entry.name
    : `#${index + 1}`;

type ConfigEntryProps = {
  value: unknown;
  path: ConfigPath;
  label: string;
  description?: ReactNode;
};

const ConfigEntry = ({ value, path, label, description }: ConfigEntryProps) => {
  const changes = useServerChanges();
  const pluginType = pluginCollections[path.join('.')];

  if (isScannerList(value)) {
    return (
      <ScannersRow label={label} description={description} value={value} />
    );
  }

  if (pluginType && !isLeafValue(value)) {
    return (
      <ConfigGroup label={label} description={description} path={path}>
        <BlockGrid>
          <PluginCollection pluginType={pluginType} value={value} path={path} />
        </BlockGrid>
      </ConfigGroup>
    );
  }

  if (isLeafValue(value) || (!Array.isArray(value) && !isRecord(value))) {
    return (
      <ConfigRow label={label} description={description} path={path}>
        <ConfigValue value={value} path={path} />
      </ConfigRow>
    );
  }

  if (Array.isArray(value)) {
    return (
      <ConfigGroup label={label} description={description} path={path}>
        <BlockGrid>
          {value.map((entry, index) => (
            <ConfigBlock
              key={index}
              title={entryTitle(entry, index)}
              path={[...path, index]}
            >
              {isRecord(entry) ? (
                <ConfigFields value={entry} path={[...path, index]} />
              ) : (
                <ConfigValue value={entry} path={[...path, index]} />
              )}
            </ConfigBlock>
          ))}
          {removedEntries(changes, path).map((entry, index) => (
            <ConfigBlock
              key={entry.path.join('.')}
              title={entryTitle(entry.requested, value.length + index)}
              path={entry.path}
            />
          ))}
        </BlockGrid>
      </ConfigGroup>
    );
  }

  return (
    <ConfigGroup label={label} description={description} path={path}>
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
 * settings missing from the table last. Settings that the server removed are shown in their
 * place.
 */
export const ConfigFields = ({
  value,
  path,
  className,
  hiddenKeys = [],
}: ConfigFieldsProps) => {
  const changes = useServerChanges();
  const isScanner = path.length === 1 && path[0] === 'scanner';
  const shown = (
    [
      ...Object.entries(value).filter(([, entry]) => entry !== undefined),
      ...removedKeys(changes, path, value).map((key): [string, unknown] => [
        key,
        undefined,
      ]),
    ] satisfies [string, unknown][]
  ).filter(([key]) => !hiddenKeys.includes(key));
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
