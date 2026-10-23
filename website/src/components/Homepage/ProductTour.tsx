import clsx from 'clsx';
import { useRef, useState, type JSX, type KeyboardEvent } from 'react';

import Icon, { type IconName } from './Icon';
import styles from './ProductTour.module.css';
import Screenshot from './Screenshot';
import SectionHeader from './SectionHeader';
import shared from './shared.module.css';

type Tab = {
  id: string;
  label: string;
  icon: IconName;
  title: string;
  description: string;
  screenshot: string;
  alt: string;
};

const tabs: Tab[] = [
  {
    id: 'packages',
    label: 'Packages',
    icon: 'package',
    title: 'A complete inventory.',
    description:
      'A de-duplicated view of every dependency, with declared licenses and source links.',
    screenshot: 'packages',
    alt: 'ORT Server web UI showing a filterable list of packages with their declared licenses and homepage links.',
  },
  {
    id: 'vulnerabilities',
    label: 'Vulnerabilities',
    icon: 'security',
    title: 'Prioritize what matters.',
    description:
      'CVSS severity, EPSS exploit probability and a resolution workflow for each finding.',
    screenshot: 'vulnerabilities',
    alt: 'ORT Server web UI showing details of a vulnerability with a CVSS severity radar chart, the EPSS score and the EPSS percentile.',
  },
  {
    id: 'licenses',
    label: 'Licenses',
    icon: 'certificate',
    title: 'Know what you ship.',
    description:
      'Every detected license, grouped and searchable down to the individual package.',
    screenshot: 'detected-licenses',
    alt: 'ORT Server web UI showing detected licenses with their package counts, with one license expanded to list the packages it was found in.',
  },
  {
    id: 'runs',
    label: 'Runs',
    icon: 'chart-stacked',
    title: 'Track every analysis.',
    description:
      'Run history with status per stage and a duration breakdown, so you can see where time goes.',
    screenshot: 'runs',
    alt: 'ORT Server web UI showing a repository with a stacked bar chart of run durations per pipeline stage and a list of runs with their status.',
  },
];

export default function ProductTour(): JSX.Element {
  const [selected, setSelected] = useState(0);
  const tabRefs = useRef<(HTMLButtonElement | null)[]>([]);

  // Arrow keys move between tabs, following the WAI-ARIA tabs pattern.
  const onKeyDown = (event: KeyboardEvent<HTMLButtonElement>) => {
    const last = tabs.length - 1;
    const targets: Record<string, number> = {
      ArrowRight: selected === last ? 0 : selected + 1,
      ArrowDown: selected === last ? 0 : selected + 1,
      ArrowLeft: selected === 0 ? last : selected - 1,
      ArrowUp: selected === 0 ? last : selected - 1,
      Home: 0,
      End: last,
    };
    const next = targets[event.key];

    if (next !== undefined) {
      event.preventDefault();
      setSelected(next);
      tabRefs.current[next]?.focus();
    }
  };

  return (
    <section className={shared.section} aria-labelledby='product-tour'>
      <div className='container'>
        <SectionHeader
          id='product-tour'
          eyebrow='Features'
          title='One place for your compliance data'
        >
          A web UI that turns ORT results into decisions.
        </SectionHeader>
        <div className={styles.tour}>
          <div
            className={styles.tabList}
            role='tablist'
            aria-label='Web UI features'
          >
            {tabs.map((tab, index) => (
              <button
                key={tab.id}
                ref={(element) => {
                  tabRefs.current[index] = element;
                }}
                id={`tour-tab-${tab.id}`}
                className={clsx(
                  styles.tab,
                  index === selected && styles.tabSelected
                )}
                type='button'
                role='tab'
                aria-selected={index === selected}
                aria-controls={`tour-panel-${tab.id}`}
                tabIndex={index === selected ? 0 : -1}
                onClick={() => setSelected(index)}
                onKeyDown={onKeyDown}
              >
                <span className={styles.tabLabel}>
                  <Icon name={tab.icon} className={styles.tabIcon} />
                  {tab.label}
                </span>
                <span className={styles.tabTitle}>{tab.title}</span>
                <span className={styles.tabDescription}>{tab.description}</span>
              </button>
            ))}
          </div>
          {tabs.map((tab, index) => (
            <div
              key={tab.id}
              id={`tour-panel-${tab.id}`}
              role='tabpanel'
              aria-labelledby={`tour-tab-${tab.id}`}
              hidden={index !== selected}
            >
              {/* On small screens the tab only shows its label. */}
              <div className={styles.panelCaption}>
                <p className={styles.tabTitle}>{tab.title}</p>
                <p className={styles.tabDescription}>{tab.description}</p>
              </div>
              <Screenshot name={tab.screenshot} alt={tab.alt} />
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
