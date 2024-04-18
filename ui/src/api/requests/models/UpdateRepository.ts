/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

export type UpdateRepository = {
    type?: UpdateRepository.type;
    url?: string;
};

export namespace UpdateRepository {

    export enum type {
        GIT = 'GIT',
        GIT_REPO = 'GIT_REPO',
        MERCURIAL = 'MERCURIAL',
        SUBVERSION = 'SUBVERSION',
        CVS = 'CVS',
    }


}

