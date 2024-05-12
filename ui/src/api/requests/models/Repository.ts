/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

export type Repository = {
  id: number;
  type: Repository.type;
  url: string;
};

export namespace Repository {
  export enum type {
    GIT = 'GIT',
    GIT_REPO = 'GIT_REPO',
    MERCURIAL = 'MERCURIAL',
    SUBVERSION = 'SUBVERSION',
    CVS = 'CVS',
  }
}
