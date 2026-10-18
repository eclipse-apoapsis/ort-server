import type { ReactNode } from 'react';

import shared from './shared.module.css';

type SectionHeaderProps = {
  id: string;
  eyebrow: string;
  title: string;
  children?: ReactNode;
};

export default function SectionHeader({
  id,
  eyebrow,
  title,
  children,
}: SectionHeaderProps): JSX.Element {
  return (
    <div className={shared.sectionHeader}>
      <span className={shared.eyebrow}>{eyebrow}</span>
      {/* A plain h2, the theme Heading would add an anchor link for the id. */}
      <h2 id={id} className={shared.sectionTitle}>
        {title}
      </h2>
      {children && <p className={shared.sectionLead}>{children}</p>}
    </div>
  );
}
