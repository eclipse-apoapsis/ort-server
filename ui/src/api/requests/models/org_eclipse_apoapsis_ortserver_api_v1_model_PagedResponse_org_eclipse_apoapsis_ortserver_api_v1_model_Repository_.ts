/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

export type org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Repository_ = {
    data: Array<{
        id: number;
        type: 'GIT' | 'GIT_REPO' | 'MERCURIAL' | 'SUBVERSION' | 'CVS';
        url: string;
    }>;
    options: {
        limit?: Record<string, any>;
        offset?: Record<string, any>;
        sortProperties?: Record<string, any>;
    };
};

