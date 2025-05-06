import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import HomepageFeatures from '@site/src/components/HomepageFeatures';
import Heading from '@theme/Heading';
import Layout from '@theme/Layout';
import clsx from 'clsx';

import styles from './index.module.css';

function HomepageHeader() {
  const { siteConfig } = useDocusaurusContext();
  return (
    <header className={clsx('hero hero--primary', styles.heroBanner)}>
      <div className='container'>
        <div className='text--center'>
          <img
            src='img/ort-server-logo.svg'
            alt='ORT Server Logo'
            className={styles.heroLogo}
          />
        </div>
        <Heading as='h1' className='hero__title'>
          {siteConfig.title}
        </Heading>
        <p className='hero__subtitle'>
          {siteConfig.tagline}
          <br />
          Based on the{' '}
          <Link to='https://oss-review-toolkit.org'>OSS Review Toolkit</Link>.
        </p>
        <div className={styles.buttons}>
          <Link
            className='button button--secondary button--lg'
            to='/docs/intro'
          >
            Introduction
          </Link>
        </div>
      </div>
    </header>
  );
}

function EclipseIncubation() {
  return (
    <section className={clsx('hero', styles.heroBanner)}>
      <div className='container'>
        <div className={styles.incubation}>
          <img
            src='https://projects.eclipse.org/modules/custom/eclipsefdn/eclipsefdn_projects/images/project_state/incubating.png'
            alt='Incubation'
            className={styles.incubationImage}
          />
          <p className='hero__subtitle'>
            The ORT Server is the reference implementation of the{' '}
            <a href='https://projects.eclipse.org/projects/technology.apoapsis'>
              Eclipse Apoapsis™
            </a>{' '}
            project which is currently in the{' '}
            <a href='https://www.eclipse.org/projects/handbook/#incubation'>
              incubation
            </a>{' '}
            phase.
          </p>
        </div>
      </div>
    </section>
  );
}

export default function Home(): JSX.Element {
  const { siteConfig } = useDocusaurusContext();
  return (
    <Layout title={`${siteConfig.title}`} description={`${siteConfig.tagline}`}>
      <HomepageHeader />
      <main>
        <HomepageFeatures />
        <EclipseIncubation />
      </main>
    </Layout>
  );
}
