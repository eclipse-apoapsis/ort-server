/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

export type CreateRepository = {
    type: CreateRepository.type;
    url: string;
};

export namespace CreateRepository {

    export enum type {
        GIT = 'GIT',
        GIT_REPO = 'GIT_REPO',
        MERCURIAL = 'MERCURIAL',
        SUBVERSION = 'SUBVERSION',
        CVS = 'CVS',
    }


}

