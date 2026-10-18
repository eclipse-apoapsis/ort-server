import { carbonIcons } from './carbonIcons';

export type IconName = keyof typeof carbonIcons;

type IconProps = {
  name: IconName;
  className?: string;
};

/** Renders one of the embedded Carbon icons, sized like the surrounding text. */
export default function Icon({ name, className }: IconProps): JSX.Element {
  return (
    <svg
      xmlns='http://www.w3.org/2000/svg'
      viewBox='0 0 32 32'
      width='1em'
      height='1em'
      className={className}
      aria-hidden='true'
      focusable='false'
      dangerouslySetInnerHTML={{ __html: carbonIcons[name] }}
    />
  );
}
