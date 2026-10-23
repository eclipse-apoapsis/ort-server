import useBaseUrl from '@docusaurus/useBaseUrl';
import ThemedImage from '@theme/ThemedImage';
import clsx from 'clsx';
import { useRef, type JSX, type MouseEvent, type SyntheticEvent } from 'react';

import Icon from './Icon';
import styles from './Screenshot.module.css';

type ScreenshotProps = {
  /** Base name of the screenshot in static/img, without theme suffix. */
  name: string;
  alt: string;
  /** Load the image immediately, for the screenshot above the fold. */
  priority?: boolean;
  className?: string;
};

/**
 * A screenshot of the web UI in a minimal browser window frame. The light or
 * dark variant is shown depending on the active theme. Clicking the screenshot
 * shows it enlarged in a dialog.
 */
export default function Screenshot({
  name,
  alt,
  priority = false,
  className,
}: ScreenshotProps): JSX.Element {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const sources = {
    light: useBaseUrl(`/img/screenshot-${name}-light.png`),
    dark: useBaseUrl(`/img/screenshot-${name}-dark.png`),
  };

  const open = () => dialogRef.current?.showModal();

  // Play the fade-out animation before actually closing the dialog.
  const close = () => {
    const dialog = dialogRef.current;
    if (!dialog?.open || dialog.dataset.closing) return;

    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      dialog.close();
      return;
    }

    const finish = () => {
      dialog.removeEventListener('animationend', onAnimationEnd);
      clearTimeout(fallback);
      delete dialog.dataset.closing;
      dialog.close();
    };
    const onAnimationEnd = (event: AnimationEvent) => {
      if (event.target === dialog) finish();
    };
    // Animations do not run in background tabs, so never rely on them alone.
    const fallback = setTimeout(finish, 300);
    dialog.addEventListener('animationend', onAnimationEnd);
    dialog.dataset.closing = 'true';
  };

  // Escape triggers a cancel event, which would close the dialog immediately.
  const onCancel = (event: SyntheticEvent<HTMLDialogElement>) => {
    event.preventDefault();
    close();
  };

  // A click on the backdrop targets the dialog element itself.
  const onDialogClick = (event: MouseEvent<HTMLDialogElement>) => {
    if (event.target === event.currentTarget) close();
  };

  return (
    <>
      <figure className={clsx(styles.frame, className)}>
        <button
          type='button'
          className={styles.zoomButton}
          onClick={open}
          aria-label={`Enlarge screenshot: ${alt}`}
        >
          <div className={styles.toolbar} aria-hidden='true'>
            <span />
            <span />
            <span />
          </div>
          <ThemedImage
            className={styles.image}
            alt={alt}
            sources={sources}
            width={1440}
            height={800}
            loading={priority ? 'eager' : 'lazy'}
            decoding='async'
            fetchPriority={priority ? 'high' : 'auto'}
          />
          <span className={styles.zoomHint} aria-hidden='true'>
            <Icon name='zoom-in' />
            Enlarge
          </span>
        </button>
      </figure>
      <dialog
        ref={dialogRef}
        className={styles.dialog}
        aria-label={alt}
        onClick={onDialogClick}
        onCancel={onCancel}
      >
        <button
          type='button'
          className={styles.closeButton}
          onClick={close}
          aria-label='Close'
        >
          <Icon name='close' />
        </button>
        <ThemedImage
          className={styles.dialogImage}
          alt={alt}
          sources={sources}
          width={1440}
          height={800}
          loading='lazy'
          decoding='async'
        />
      </dialog>
    </>
  );
}
