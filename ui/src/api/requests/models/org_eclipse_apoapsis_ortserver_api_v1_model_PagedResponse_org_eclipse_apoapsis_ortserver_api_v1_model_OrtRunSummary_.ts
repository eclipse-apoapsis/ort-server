/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

export type org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_OrtRunSummary_ =
  {
    data: Array<{
      id: number;
      index: number;
      repositoryId: number;
      revision: string;
      path?: Record<string, any>;
      createdAt: string;
      finishedAt?: Record<string, any>;
      jobs: {
        analyzer?: {
          id: number;
          createdAt: string;
          startedAt?: Record<string, any>;
          finishedAt?: Record<string, any>;
          status: 'CREATED' | 'SCHEDULED' | 'RUNNING' | 'FAILED' | 'FINISHED';
        };
        advisor?: {
          id: number;
          createdAt: string;
          startedAt?: Record<string, any>;
          finishedAt?: Record<string, any>;
          status: 'CREATED' | 'SCHEDULED' | 'RUNNING' | 'FAILED' | 'FINISHED';
        };
        scanner?: {
          id: number;
          createdAt: string;
          startedAt?: Record<string, any>;
          finishedAt?: Record<string, any>;
          status: 'CREATED' | 'SCHEDULED' | 'RUNNING' | 'FAILED' | 'FINISHED';
        };
        evaluator?: {
          id: number;
          createdAt: string;
          startedAt?: Record<string, any>;
          finishedAt?: Record<string, any>;
          status: 'CREATED' | 'SCHEDULED' | 'RUNNING' | 'FAILED' | 'FINISHED';
        };
        reporter?: {
          id: number;
          createdAt: string;
          startedAt?: Record<string, any>;
          finishedAt?: Record<string, any>;
          status: 'CREATED' | 'SCHEDULED' | 'RUNNING' | 'FAILED' | 'FINISHED';
        };
      };
      status: 'CREATED' | 'ACTIVE' | 'FINISHED' | 'FAILED';
      labels: Record<string, string>;
      jobConfigContext?: Record<string, any>;
      resolvedJobConfigContext?: Record<string, any>;
    }>;
    options: {
      limit?: Record<string, any>;
      offset?: Record<string, any>;
      sortProperties?: Record<string, any>;
    };
  };
