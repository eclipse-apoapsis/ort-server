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

// Compares the totals of two Vitest coverage summaries in the `json-summary`
// format (`coverage/coverage-summary.json`) and prints them as a Markdown
// report.
//
// Usage:
//   node scripts/coverage-summary.mjs --current <path> --current-sha <sha>
//     [--base <path> --base-sha <sha>]
//     [--base-sha <sha> --base-status missing|failed]
//     [--repository-url <url>]
//
// Without any base option, the report shows the current totals only, as for a
// push to `main`. With a repository URL, the commit SHAs link to the commits.

import { readFileSync } from 'node:fs';
import process from 'node:process';
import { pathToFileURL } from 'node:url';
import { parseArgs } from 'node:util';

/** The metrics in the order the report shows them. */
export const METRICS = [
  { key: 'lines', label: 'Lines' },
  { key: 'statements', label: 'Statements' },
  { key: 'functions', label: 'Functions' },
  { key: 'branches', label: 'Branches' },
];

/** The CI workflow reports coverage only for this branch and pull requests to it. */
const BASE_BRANCH = 'main';

const SHA_PATTERN = /^[0-9a-f]{7,40}$/;

// Only a plain repository URL, so that it cannot break the Markdown link.
const REPOSITORY_URL_PATTERN =
  /^https:\/\/[A-Za-z0-9.-]+\/[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/;

// U+2212, which is as wide as the plus sign, unlike the hyphen.
const MINUS = '−';

/**
 * Check the `total` entry of a parsed coverage summary and return the
 * percentage of each of the four metrics, or `null` for a metric with nothing
 * to count. `name` is used in error messages.
 */
export function readTotals(summary, name) {
  const total = summary?.total;
  if (typeof total !== 'object' || total === null) {
    throw new Error(`${name}: the summary has no "total" entry.`);
  }

  return Object.fromEntries(
    METRICS.map(({ key }) => {
      const metric = total[key];
      if (typeof metric !== 'object' || metric === null) {
        throw new Error(`${name}: "total.${key}" is missing.`);
      }

      const { covered, total: count, pct } = metric;
      for (const [field, value] of [
        ['covered', covered],
        ['total', count],
      ]) {
        if (!Number.isInteger(value) || value < 0) {
          throw new Error(
            `${name}: "total.${key}.${field}" must be a whole number of at least 0, but is ${JSON.stringify(value)}.`
          );
        }
      }
      if (covered > count) {
        throw new Error(
          `${name}: "total.${key}" has ${covered} covered of only ${count}.`
        );
      }
      // A total of 0 has no meaningful percentage, whatever the file says.
      if (count > 0 && !(Number.isFinite(pct) && pct >= 0 && pct <= 100)) {
        throw new Error(
          `${name}: "total.${key}.pct" must be a number from 0 to 100, but is ${JSON.stringify(pct)}.`
        );
      }

      return [key, count > 0 ? pct : null];
    })
  );
}

/** Read and check a coverage summary file. */
export function readSummaryFile(path) {
  let text;
  try {
    text = readFileSync(path, 'utf8');
  } catch (error) {
    throw new Error(`${path}: cannot read the file (${error.code}).`, {
      cause: error,
    });
  }

  let summary;
  try {
    summary = JSON.parse(text);
  } catch (error) {
    throw new Error(`${path}: the file is not valid JSON (${error.message}).`, {
      cause: error,
    });
  }

  return readTotals(summary, path);
}

/** Format a percentage, or "n/a" for a metric with nothing to count. */
export function formatPercent(pct) {
  return pct === null ? 'n/a' : `${pct.toFixed(2)}%`;
}

/**
 * Format the difference between two percentages in percentage points, with a
 * green circle for an increase and a red circle for a decrease.
 */
export function formatChange(basePct, currentPct) {
  if (basePct === null || currentPct === null) return 'n/a';

  // Round the difference of the shown percentages, so that the change always
  // matches the two numbers next to it.
  const hundredths = Math.round((currentPct - basePct) * 100);
  const text = (Math.abs(hundredths) / 100).toFixed(2);

  if (hundredths > 0) return `🟢 +${text} pp`;
  if (hundredths < 0) return `🔴 ${MINUS}${text} pp`;
  return `${text} pp`;
}

// <samp> shows the SHA in a smaller monospace font, without the background of
// a code span.
function formatSha(sha, repositoryUrl) {
  const text = `<samp>${sha.slice(0, 7)}</samp>`;
  return repositoryUrl === undefined
    ? text
    : `[${text}](${repositoryUrl}/commit/${sha})`;
}

// Columns are left-aligned explicitly, because browsers centre header cells
// without an alignment. Only "Change" is right-aligned, so that its signs and
// decimal points line up.
function table(header, rows) {
  const align = header.map((title) => (title === 'Change' ? '---:' : ':---'));
  return [header, align, ...rows].map((row) => `| ${row.join(' | ')} |`);
}

const BASE_STATUS_NOTES = {
  missing:
    'The base commit has no UI test coverage, because it is older than coverage reporting. Changes cannot be shown.',
  failed:
    'Measuring the coverage of the base commit failed. Changes cannot be shown; see the workflow run for the error.',
};

/**
 * Render the report.
 *
 * - With `base`, it compares the current totals with the base totals.
 * - With `baseSha` and `baseStatus` but no `base`, it shows the current totals
 *   and says why the base is missing.
 * - Without `baseSha`, it shows the current totals of a commit on `main`.
 *
 * With `repositoryUrl`, the commit SHAs link to the commits.
 */
export function renderReport({
  current,
  currentSha,
  base,
  baseSha,
  baseStatus,
  repositoryUrl,
}) {
  for (const [name, sha] of [
    ['current', currentSha],
    ['base', baseSha],
  ]) {
    if (sha !== undefined && !SHA_PATTERN.test(sha)) {
      throw new Error(
        `The ${name} SHA must be 7 to 40 lowercase hexadecimal characters, but is ${JSON.stringify(sha)}.`
      );
    }
  }
  if (currentSha === undefined) throw new Error('The current SHA is missing.');
  if (base !== undefined && baseSha === undefined) {
    throw new Error('A base summary needs the base SHA.');
  }
  if (base !== undefined && baseStatus !== undefined) {
    throw new Error('A base summary and a base status cannot be combined.');
  }
  if (baseStatus !== undefined && !(baseStatus in BASE_STATUS_NOTES)) {
    throw new Error(
      `The base status must be one of ${Object.keys(BASE_STATUS_NOTES).join(', ')}, but is ${JSON.stringify(baseStatus)}.`
    );
  }
  if (baseSha !== undefined && base === undefined && baseStatus === undefined) {
    throw new Error('A base SHA needs a base summary or a base status.');
  }
  if (
    repositoryUrl !== undefined &&
    !REPOSITORY_URL_PATTERN.test(repositoryUrl)
  ) {
    throw new Error(
      `The repository URL must look like https://github.com/owner/name, but is ${JSON.stringify(repositoryUrl)}.`
    );
  }

  const lines = ['### UI test coverage', ''];

  if (baseSha === undefined) {
    lines.push(
      `Commit ${formatSha(currentSha, repositoryUrl)} on \`${BASE_BRANCH}\``,
      ''
    );
  } else {
    const baseLabel = `\`${BASE_BRANCH}\` at ${formatSha(baseSha, repositoryUrl)}`;
    lines.push(
      `Base: ${baseLabel} · PR: ${formatSha(currentSha, repositoryUrl)} merged into ${baseLabel}`,
      ''
    );
  }

  if (base === undefined) {
    if (baseStatus !== undefined) {
      lines.push(`> [!NOTE]`, `> ${BASE_STATUS_NOTES[baseStatus]}`, '');
    }
    lines.push(
      ...table(
        ['Metric', 'Coverage'],
        METRICS.map(({ key, label }) => [label, formatPercent(current[key])])
      )
    );
  } else {
    lines.push(
      'pp = percentage points',
      '',
      ...table(
        ['Metric', 'Base', 'PR', 'Change'],
        METRICS.map(({ key, label }) => [
          label,
          formatPercent(base[key]),
          formatPercent(current[key]),
          formatChange(base[key], current[key]),
        ])
      )
    );
  }

  return `${lines.join('\n')}\n`;
}

function main(args) {
  const { values } = parseArgs({
    args,
    options: {
      current: { type: 'string' },
      'current-sha': { type: 'string' },
      base: { type: 'string' },
      'base-sha': { type: 'string' },
      'base-status': { type: 'string' },
      'repository-url': { type: 'string' },
    },
    strict: true,
  });

  if (values.current === undefined) {
    throw new Error('The --current summary is missing.');
  }

  return renderReport({
    current: readSummaryFile(values.current),
    currentSha: values['current-sha'],
    base: values.base === undefined ? undefined : readSummaryFile(values.base),
    baseSha: values['base-sha'],
    baseStatus: values['base-status'],
    repositoryUrl: values['repository-url'],
  });
}

if (
  process.argv[1] !== undefined &&
  import.meta.url === pathToFileURL(process.argv[1]).href
) {
  try {
    process.stdout.write(main(process.argv.slice(2)));
  } catch (error) {
    process.stderr.write(`coverage-summary: ${error.message}\n`);
    process.exitCode = 1;
  }
}
