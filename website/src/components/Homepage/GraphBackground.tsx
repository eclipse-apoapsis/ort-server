import clsx from 'clsx';
import { useEffect, useMemo, useRef, type JSX } from 'react';

import styles from './GraphBackground.module.css';

const WIDTH = 1440;
const HEIGHT = 720;
const COLUMNS = 17;
const ROWS = 9;
const SPACING = 90;

const TONES = {
  crimson: '#be3459',
  teal: '#26a5a1',
  purple: '#634caf',
} as const;

type Tone = keyof typeof TONES;

// Lighter tones for use on a strongly colored background.
const ON_COLOR_TONES: Record<Tone, string> = {
  crimson: '#ffffff',
  teal: '#9acdcc',
  purple: '#e7e4f3',
};

/** A highlighted node has a tone and a delay for its pulse animation. */
type Node = { x: number; y: number; tone?: Tone; pulseDelay?: number };
type Edge = { to: Node; path: string };
type Route = { path: string; tone: Tone; duration: number; delay: number };

// A seeded random number generator, so that server and client render the same
// graph and hydration does not change it.
function random(seed: number) {
  return () => {
    seed = (seed + 0x6d2b79f5) | 0;
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** An S-shaped curve from one node to the next column. */
function curve(from: Node, to: Node) {
  const middle = (from.x + to.x) / 2;
  return `M${from.x} ${from.y}C${middle} ${from.y} ${middle} ${to.y} ${to.x} ${to.y}`;
}

/**
 * Builds a graph that resembles a dependency tree flowing from left to right.
 * Some nodes are highlighted like findings in a compliance report, and a few
 * routes through the graph are used for "packets" travelling along the edges.
 */
function buildGraph(seed: number) {
  const next = random(seed);
  const tones = Object.keys(TONES) as Tone[];

  const grid: Node[][] = [];
  let highlighted = 0;
  for (let column = 0; column < COLUMNS; column++) {
    grid.push([]);
    for (let row = 0; row < ROWS; row++) {
      const isHighlighted = next() < 0.14;
      const node: Node = {
        x: column * SPACING + (next() - 0.5) * 44,
        y: row * SPACING + (next() - 0.5) * 44,
      };
      if (isHighlighted) {
        node.tone = tones[Math.floor(next() * 3)];
        node.pulseDelay = (highlighted++ % 7) * 0.8;
      }
      grid[column].push(node);
    }
  }

  const edges: Edge[] = [];
  const outgoing = new Map<Node, Edge[]>();
  const connect = (from: Node, to: Node) => {
    const edge = { to, path: curve(from, to) };
    edges.push(edge);
    outgoing.set(from, [...(outgoing.get(from) ?? []), edge]);
  };

  for (let column = 0; column < COLUMNS - 1; column++) {
    for (let row = 0; row < ROWS; row++) {
      if (next() < 0.55) connect(grid[column][row], grid[column + 1][row]);
      const diagonal = row + (next() < 0.5 ? -1 : 1);
      if (diagonal >= 0 && diagonal < ROWS && next() < 0.4) {
        connect(grid[column][row], grid[column + 1][diagonal]);
      }
    }
  }

  // Random walks along the edges, used as motion paths for the packets.
  const routes: Route[] = [];
  for (let attempt = 0; routes.length < 7 && attempt < 100; attempt++) {
    let node = grid[Math.floor(next() * 6)][Math.floor(next() * ROWS)];
    const segments: string[] = [];
    for (let step = 0; step < 8; step++) {
      const options = outgoing.get(node);
      if (!options) break;
      const edge = options[Math.floor(next() * options.length)];
      segments.push(
        segments.length ? edge.path.replace(/^M[^C]+/, '') : edge.path
      );
      node = edge.to;
    }
    if (segments.length >= 4) {
      routes.push({
        path: segments.join(''),
        tone: tones[routes.length % 3],
        duration: segments.length * 1.6 + next() * 2,
        delay: routes.length * 1.7,
      });
    }
  }

  return { nodes: grid.flat(), edges, routes };
}

type GraphBackgroundProps = {
  /** Prefix for the SVG gradient ids, unique per page. */
  id: string;
  seed: number;
  /**
   * 'side' keeps the graph faint behind content on the left, 'center' behind
   * centered content.
   */
  fade: 'side' | 'center';
  /** Use light colors, for a background with a strong color. */
  onColor?: boolean;
};

/**
 * A decorative background with soft color fields and an animated graph that
 * resembles a dependency tree.
 */
export default function GraphBackground({
  id,
  seed,
  fade,
  onColor = false,
}: GraphBackgroundProps): JSX.Element {
  const tones: Record<Tone, string> = onColor ? ON_COLOR_TONES : TONES;
  const svgRef = useRef<SVGSVGElement>(null);
  const graph = useMemo(() => buildGraph(seed), [seed]);

  // SMIL animations cannot be stopped with CSS, so pause them explicitly when
  // the user prefers reduced motion.
  useEffect(() => {
    const query = window.matchMedia('(prefers-reduced-motion: reduce)');
    const update = () => {
      if (query.matches) svgRef.current?.pauseAnimations();
      else svgRef.current?.unpauseAnimations();
    };
    update();
    query.addEventListener('change', update);
    return () => query.removeEventListener('change', update);
  }, []);

  return (
    <div
      className={clsx(
        styles.background,
        styles[fade],
        onColor && styles.onColor
      )}
      aria-hidden='true'
    >
      <div className={styles.aurora} />
      <svg
        ref={svgRef}
        className={styles.graph}
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        preserveAspectRatio='xMidYMid slice'
      >
        <defs>
          <linearGradient
            id={`${id}-edge`}
            gradientUnits='userSpaceOnUse'
            x1='0'
            y1='0'
            x2={WIDTH}
            y2={HEIGHT}
          >
            <stop offset='0%' stopColor={tones.crimson} />
            <stop offset='55%' stopColor={tones.purple} />
            <stop offset='100%' stopColor={tones.teal} />
          </linearGradient>
          {(Object.keys(TONES) as Tone[]).map((tone) => (
            <radialGradient key={tone} id={`${id}-glow-${tone}`}>
              <stop offset='0%' stopColor={tones[tone]} stopOpacity='0.7' />
              <stop offset='45%' stopColor={tones[tone]} stopOpacity='0.2' />
              <stop offset='100%' stopColor={tones[tone]} stopOpacity='0' />
            </radialGradient>
          ))}
        </defs>

        <g className={styles.edges} stroke={`url(#${id}-edge)`}>
          {graph.edges.map((edge, index) => (
            <path key={index} d={edge.path} />
          ))}
        </g>

        {graph.nodes.map((node, index) => {
          if (!node.tone) {
            return (
              <circle
                key={index}
                className={styles.node}
                cx={node.x}
                cy={node.y}
                r={2.5}
              />
            );
          }
          return (
            <g key={index}>
              <circle
                cx={node.x}
                cy={node.y}
                r={18}
                fill={`url(#${id}-glow-${node.tone})`}
              >
                <animate
                  attributeName='r'
                  values='14;30;14'
                  dur='5.6s'
                  begin={`-${node.pulseDelay}s`}
                  repeatCount='indefinite'
                  calcMode='spline'
                  keyTimes='0;0.5;1'
                  keySplines='0.45 0 0.55 1;0.45 0 0.55 1'
                />
              </circle>
              <circle cx={node.x} cy={node.y} r={5} fill={tones[node.tone]} />
            </g>
          );
        })}

        {graph.routes.map((route, index) => (
          <g key={index} className={styles.packet} opacity='0'>
            <circle r={9} fill={`url(#${id}-glow-${route.tone})`} />
            <circle r={2.5} fill={tones[route.tone]} />
            <animateMotion
              dur={`${route.duration}s`}
              begin={`-${route.delay}s`}
              repeatCount='indefinite'
              path={route.path}
            />
            <animate
              attributeName='opacity'
              values='0;1;1;0'
              keyTimes='0;0.1;0.85;1'
              dur={`${route.duration}s`}
              begin={`-${route.delay}s`}
              repeatCount='indefinite'
            />
          </g>
        ))}
      </svg>
    </div>
  );
}
