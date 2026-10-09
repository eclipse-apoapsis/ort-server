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

import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import process from 'node:process';
import { after, describe, it } from 'node:test';
import { fileURLToPath, URL } from 'node:url';

import {
  formatChange,
  formatPercent,
  readTotals,
  renderReport,
} from './coverage-summary.mjs';

const SCRIPT = fileURLToPath(
  new URL('./coverage-summary.mjs', import.meta.url)
);

const BASE_SHA = 'ecd6689c6aa0aa10b9565acbc4e049d3e2c61d60';
const HEAD_SHA = '753f39c5a2bf8c9d585341146948f71e6932d33f';

function metric(covered, total, pct) {
  return { total, covered, skipped: 0, pct };
}

function summary(overrides = {}) {
  return {
    total: {
      lines: metric(7763, 11608, 66.87),
      statements: metric(11700, 17515, 66.79),
      functions: metric(1626, 3069, 52.98),
      branches: metric(8981, 15449, 58.13),
      branchesTrue: metric(0, 0, 100),
      ...overrides,
    },
    '/some/file.ts': { lines: metric(1, 2, 50) },
  };
}

const BASE = summary();
const CURRENT = summary({
  lines: metric(7800, 11625, 67.1),
  statements: metric(11760, 17521, 67.12),
  functions: metric(1633, 3070, 53.2),
  branches: metric(8981, 15484, 58),
});

function tableRow(report, label) {
  return report.split('\n').find((line) => line.startsWith(`| ${label} |`));
}

describe('formatChange', () => {
  it('marks an increase green and a decrease red', () => {
    assert.equal(formatChange(66.87, 67.1), '🟢 +0.23 pp');
    assert.equal(formatChange(58.13, 58), '🔴 −0.13 pp');
  });

  it('shows no circle and no sign for an unchanged value', () => {
    assert.equal(formatChange(50, 50), '0.00 pp');
  });

  it('rounds the difference of the shown percentages', () => {
    // 0.1 + 0.2 is not exactly 0.3 in floating point.
    assert.equal(formatChange(0.1, 0.4), '🟢 +0.30 pp');
    assert.equal(formatChange(66.79, 66.8), '🟢 +0.01 pp');
  });

  it('never shows a negative zero', () => {
    assert.equal(formatChange(50.001, 50), '0.00 pp');
    assert.equal(formatChange(0, -0), '0.00 pp');
  });

  it('shows n/a when either side has nothing to count', () => {
    assert.equal(formatChange(null, 50), 'n/a');
    assert.equal(formatChange(50, null), 'n/a');
  });
});

describe('formatPercent', () => {
  it('shows two decimal places', () => {
    assert.equal(formatPercent(67.1), '67.10%');
    assert.equal(formatPercent(100), '100.00%');
  });

  it('shows n/a for a metric with nothing to count', () => {
    assert.equal(formatPercent(null), 'n/a');
  });
});

describe('readTotals', () => {
  it('reads the percentages of the four metrics of the total entry', () => {
    assert.deepEqual(readTotals(BASE, 'base'), {
      lines: 66.87,
      statements: 66.79,
      functions: 52.98,
      branches: 58.13,
    });
  });

  it('ignores the percentage of a metric with a total of 0', () => {
    const totals = readTotals(
      summary({ functions: metric(0, 0, 100) }),
      'base'
    );
    assert.equal(totals.functions, null);
  });

  const invalid = [
    ['no total entry', {}, /no "total" entry/],
    [
      'a missing metric',
      summary({ branches: undefined }),
      /"total.branches" is missing/,
    ],
    [
      'a covered count that is not a number',
      summary({ lines: metric('7763', 11608, 66.87) }),
      /"total.lines.covered" must be a whole number/,
    ],
    [
      'a negative total',
      summary({ lines: metric(0, -1, 0) }),
      /"total.lines.total" must be a whole number of at least 0/,
    ],
    [
      'a fractional count',
      summary({ lines: metric(1.5, 3, 50) }),
      /"total.lines.covered" must be a whole number/,
    ],
    [
      'more covered than total',
      summary({ lines: metric(4, 3, 100) }),
      /has 4 covered of only 3/,
    ],
    [
      'a percentage that is not a number',
      summary({ lines: metric(1, 2, 'Unknown') }),
      /"total.lines.pct" must be a number from 0 to 100/,
    ],
    [
      'a percentage above 100',
      summary({ lines: metric(1, 2, 150) }),
      /"total.lines.pct" must be a number from 0 to 100/,
    ],
  ];

  for (const [description, input, message] of invalid) {
    it(`rejects ${description}`, () => {
      assert.throws(() => readTotals(input, 'base'), message);
    });
  }
});

describe('renderReport', () => {
  const base = readTotals(BASE, 'base');
  const current = readTotals(CURRENT, 'current');

  it('compares the four metrics in a fixed order', () => {
    const report = renderReport({
      current,
      currentSha: HEAD_SHA,
      base,
      baseSha: BASE_SHA,
    });

    const rows = report
      .split('\n')
      .filter((line) =>
        /^\| (Lines|Statements|Functions|Branches) \|/.test(line)
      )
      .slice(0, 4);
    assert.deepEqual(rows, [
      '| Lines | 66.87% | 67.10% | 🟢 +0.23 pp |',
      '| Statements | 66.79% | 67.12% | 🟢 +0.33 pp |',
      '| Functions | 52.98% | 53.20% | 🟢 +0.22 pp |',
      '| Branches | 58.13% | 58.00% | 🔴 −0.13 pp |',
    ]);
    assert.match(report, /^pp = percentage points$/m);
    assert.match(report, /^\| :--- \| :--- \| :--- \| ---: \|$/m);
  });

  it('names the two real commits of the comparison', () => {
    const report = renderReport({
      current,
      currentSha: HEAD_SHA,
      base,
      baseSha: BASE_SHA,
    });

    assert.match(
      report,
      /^Base: `main` at <samp>ecd6689<\/samp> · PR: <samp>753f39c<\/samp> merged into `main` at <samp>ecd6689<\/samp>$/m
    );
  });

  it('links the commits when the repository URL is given', () => {
    const repositoryUrl = 'https://github.com/eclipse-apoapsis/ort-server';
    const baseLink = `[<samp>ecd6689</samp>](${repositoryUrl}/commit/${BASE_SHA})`;
    const headLink = `[<samp>753f39c</samp>](${repositoryUrl}/commit/${HEAD_SHA})`;

    const comparison = renderReport({
      current,
      currentSha: HEAD_SHA,
      base,
      baseSha: BASE_SHA,
      repositoryUrl,
    });
    assert.ok(
      comparison.includes(
        `Base: \`main\` at ${baseLink} · PR: ${headLink} merged into \`main\` at ${baseLink}\n`
      ),
      comparison
    );

    const push = renderReport({ current, currentSha: HEAD_SHA, repositoryUrl });
    assert.ok(push.includes(`Commit ${headLink} on \`main\`\n`), push);
  });

  it('shows n/a for a metric with nothing to count', () => {
    const report = renderReport({
      current: readTotals(summary({ functions: metric(0, 0, 100) }), 'current'),
      currentSha: HEAD_SHA,
      base,
      baseSha: BASE_SHA,
    });

    assert.equal(
      tableRow(report, 'Functions'),
      '| Functions | 52.98% | n/a | n/a |'
    );
  });

  it('shows the current totals and a note when the base has no coverage', () => {
    const report = renderReport({
      current,
      currentSha: HEAD_SHA,
      baseSha: BASE_SHA,
      baseStatus: 'missing',
    });

    assert.match(report, /older than coverage reporting/);
    assert.equal(tableRow(report, 'Lines'), '| Lines | 67.10% |');
    assert.doesNotMatch(report, /pp/);
  });

  it('shows the current totals and a note when the base coverage failed', () => {
    const report = renderReport({
      current,
      currentSha: HEAD_SHA,
      baseSha: BASE_SHA,
      baseStatus: 'failed',
    });

    assert.match(report, /Measuring the coverage of the base commit failed/);
    assert.doesNotMatch(report, /pp/);
  });

  it('shows the current totals of a commit on main', () => {
    const report = renderReport({ current, currentSha: HEAD_SHA });

    assert.match(report, /^Commit <samp>753f39c<\/samp> on `main`$/m);
    assert.doesNotMatch(report, /Base|pp/);
    assert.equal(tableRow(report, 'Branches'), '| Branches | 58.00% |');
  });

  const invalid = [
    [
      'a SHA that could break the Markdown',
      { current, currentSha: 'abc1234 | x' },
      /current SHA must be 7 to 40 lowercase hexadecimal characters/,
    ],
    [
      'a base SHA that is not hexadecimal',
      { current, currentSha: HEAD_SHA, base, baseSha: 'main' },
      /base SHA must be/,
    ],
    ['a missing current SHA', { current }, /current SHA is missing/],
    [
      'a repository URL that could break the Markdown',
      {
        current,
        currentSha: HEAD_SHA,
        repositoryUrl:
          'https://github.com/owner/name) [x](https://evil.example',
      },
      /repository URL must look like/,
    ],
    [
      'a base summary without a base SHA',
      { current, currentSha: HEAD_SHA, base },
      /needs the base SHA/,
    ],
    [
      'a base SHA without a base summary or status',
      { current, currentSha: HEAD_SHA, baseSha: BASE_SHA },
      /needs a base summary or a base status/,
    ],
    [
      'an unknown base status',
      { current, currentSha: HEAD_SHA, baseSha: BASE_SHA, baseStatus: 'gone' },
      /base status must be one of missing, failed/,
    ],
    [
      'a base summary together with a base status',
      {
        current,
        currentSha: HEAD_SHA,
        base,
        baseSha: BASE_SHA,
        baseStatus: 'failed',
      },
      /cannot be combined/,
    ],
  ];

  for (const [description, input, message] of invalid) {
    it(`rejects ${description}`, () => {
      assert.throws(() => renderReport(input), message);
    });
  }
});

describe('the command line', () => {
  const directory = mkdtempSync(join(tmpdir(), 'coverage-summary-'));
  after(() => rmSync(directory, { recursive: true, force: true }));

  function file(name, content) {
    const path = join(directory, name);
    writeFileSync(
      path,
      typeof content === 'string' ? content : JSON.stringify(content)
    );
    return path;
  }

  function run(...args) {
    return spawnSync(process.execPath, [SCRIPT, ...args], { encoding: 'utf8' });
  }

  const basePath = file('base.json', BASE);
  const currentPath = file('current.json', CURRENT);

  it('prints the comparison to standard output', () => {
    const result = run(
      '--current',
      currentPath,
      '--current-sha',
      HEAD_SHA,
      '--base',
      basePath,
      '--base-sha',
      BASE_SHA
    );

    assert.equal(result.status, 0, result.stderr);
    assert.equal(result.stderr, '');
    assert.equal(
      result.stdout,
      renderReport({
        current: readTotals(CURRENT, 'current'),
        currentSha: HEAD_SHA,
        base: readTotals(BASE, 'base'),
        baseSha: BASE_SHA,
      })
    );
  });

  it('prints the current totals when the base has no coverage', () => {
    const result = run(
      '--current',
      currentPath,
      '--current-sha',
      HEAD_SHA,
      '--base-sha',
      BASE_SHA,
      '--base-status',
      'missing'
    );

    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /older than coverage reporting/);
  });

  const failures = [
    [
      'a base file that does not exist',
      ['--base', join(directory, 'missing.json'), '--base-sha', BASE_SHA],
      /missing\.json: cannot read the file \(ENOENT\)/,
    ],
    [
      'a base file with broken JSON',
      ['--base', file('broken.json', '{"total":'), '--base-sha', BASE_SHA],
      /broken\.json: the file is not valid JSON/,
    ],
    [
      'a base file with invalid counts',
      [
        '--base',
        file('invalid.json', summary({ lines: metric(4, 3, 100) })),
        '--base-sha',
        BASE_SHA,
      ],
      /invalid\.json: "total.lines" has 4 covered of only 3/,
    ],
    ['an unknown option', ['--bogus', 'x'], /Unknown option '--bogus'/],
  ];

  for (const [description, args, message] of failures) {
    it(`fails with a clear error for ${description}`, () => {
      const result = run(
        '--current',
        currentPath,
        '--current-sha',
        HEAD_SHA,
        ...args
      );

      assert.equal(result.status, 1);
      assert.equal(result.stdout, '');
      assert.match(result.stderr, message);
    });
  }

  it('fails with a clear error without a current summary', () => {
    const result = run('--current-sha', HEAD_SHA);

    assert.equal(result.status, 1);
    assert.match(result.stderr, /The --current summary is missing/);
  });
});
