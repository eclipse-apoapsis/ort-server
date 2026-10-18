import Link from '@docusaurus/Link';
import useBaseUrl from '@docusaurus/useBaseUrl';
import Heading from '@theme/Heading';
import clsx from 'clsx';

import GraphBackground from './GraphBackground';
import styles from './Hero.module.css';
import Icon from './Icon';
import Screenshot from './Screenshot';
import shared from './shared.module.css';

export default function Hero(): JSX.Element {
  return (
    <header className={styles.hero}>
      <GraphBackground id='hero-graph' seed={42} fade='side' />
      <div className={clsx('container', styles.inner)}>
        <div>
          <p className={styles.eyebrow}>
            <img
              src={useBaseUrl('/img/ort-server-logo.svg')}
              alt=''
              className={styles.eyebrowLogo}
            />
            <span className={styles.eyebrowName}>ORT Server</span>
            <span className={styles.eyebrowDivider} aria-hidden='true' />
            <span>
              <span className={styles.eyebrowOpenSource}>Open source · </span>
              <span className={styles.eyebrowProject}>Eclipse Apoapsis</span>
            </span>
          </p>
          <Heading as='h1' className={styles.title}>
            Software compliance,{' '}
            <span className={styles.highlight}>automated at scale</span>
          </Heading>
          <p className={styles.subtitle}>
            ORT Server runs the OSS Review Toolkit as a service. Use it to
            analyze dependencies, licenses and vulnerabilities across all
            projects in your organization.
          </p>
          <div className={shared.buttons}>
            <Link
              className={clsx(shared.button, shared.buttonPrimary)}
              to='/docs/intro'
            >
              Get started
              <Icon name='arrow-right' />
            </Link>
            <Link
              className={clsx(shared.button, shared.buttonSecondary)}
              to='/api/ort-server-api'
            >
              API reference
            </Link>
            <Link
              className={clsx(shared.button, shared.buttonGhost)}
              href='https://github.com/eclipse-apoapsis/ort-server'
            >
              <Icon name='logo-github' />
              GitHub
            </Link>
          </div>
        </div>
        <Screenshot
          className={styles.screenshot}
          name='organization'
          alt='ORT Server web UI showing an organization overview with counts of vulnerabilities, issues, rule violations and packages, and a list of products with their latest run status.'
          priority
        />
      </div>
    </header>
  );
}
