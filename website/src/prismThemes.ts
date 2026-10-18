import type { PrismTheme } from 'prism-react-renderer';

/*
 * Syntax highlighting themes based on the ORT Server design palette. All token
 * colors have a contrast of at least 5:1 against the code block background.
 */

export const lightCodeTheme: PrismTheme = {
  plain: { color: '#333b43', backgroundColor: '#f5f5f5' },
  styles: [
    {
      types: ['comment', 'prolog', 'doctype', 'cdata'],
      style: { color: '#5f6872', fontStyle: 'italic' },
    },
    { types: ['punctuation', 'operator'], style: { color: '#495560' } },
    {
      types: ['keyword', 'selector', 'important', 'atrule', 'builtin'],
      style: { color: '#be3459' },
    },
    {
      types: ['property', 'attr-name', 'tag', 'key'],
      style: { color: '#634caf' },
    },
    {
      types: ['string', 'char', 'attr-value', 'regex', 'url'],
      style: { color: '#19706d' },
    },
    {
      types: ['number', 'boolean', 'constant', 'symbol'],
      style: { color: '#805400' },
    },
    { types: ['function', 'class-name'], style: { color: '#2d6b80' } },
    { types: ['inserted'], style: { color: '#007300' } },
    { types: ['deleted'], style: { color: '#af272b' } },
  ],
};

export const darkCodeTheme: PrismTheme = {
  plain: { color: '#e1e2e3', backgroundColor: '#22272d' },
  styles: [
    {
      types: ['comment', 'prolog', 'doctype', 'cdata'],
      style: { color: '#9aa1a9', fontStyle: 'italic' },
    },
    { types: ['punctuation', 'operator'], style: { color: '#ccced0' } },
    {
      types: ['keyword', 'selector', 'important', 'atrule', 'builtin'],
      style: { color: '#e07a96' },
    },
    {
      types: ['property', 'attr-name', 'tag', 'key'],
      style: { color: '#b3a7dd' },
    },
    {
      types: ['string', 'char', 'attr-value', 'regex', 'url'],
      style: { color: '#66cbc8' },
    },
    {
      types: ['number', 'boolean', 'constant', 'symbol'],
      style: { color: '#ffdd80' },
    },
    { types: ['function', 'class-name'], style: { color: '#7fd3f0' } },
    { types: ['inserted'], style: { color: '#80d280' } },
    { types: ['deleted'], style: { color: '#fd9c9f' } },
  ],
};
