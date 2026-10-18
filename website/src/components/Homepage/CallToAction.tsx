import Link from '@docusaurus/Link';
import clsx from 'clsx';

import styles from './CallToAction.module.css';
import GraphBackground from './GraphBackground';
import Icon from './Icon';
import shared from './shared.module.css';

export default function CallToAction(): JSX.Element {
  return (
    <section className={styles.section} aria-labelledby='get-started'>
      <div className='container'>
        <div className={styles.band}>
          <GraphBackground id='cta-graph' seed={7} fade='center' onColor />
          <h2 id='get-started' className={styles.title}>
            Ready to automate your compliance checks?
          </h2>
          <p className={styles.text}>
            Deploy ORT Server and run your first analysis.
          </p>
          <div className={clsx(shared.buttons, styles.buttons)}>
            <Link
              className={clsx(shared.button, styles.primary)}
              to='/docs/intro'
            >
              Read the docs
              <Icon name='arrow-right' />
            </Link>
            <Link
              className={clsx(shared.button, styles.secondary)}
              href='https://github.com/eclipse-apoapsis/ort-server'
            >
              <Icon name='logo-github' />
              View on GitHub
            </Link>
          </div>
        </div>
      </div>
    </section>
  );
}
