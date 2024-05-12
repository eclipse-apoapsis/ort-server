/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

export type org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_InfrastructureService_ =
  {
    data: Array<{
      name: string;
      url: string;
      description?: Record<string, any>;
      usernameSecretRef: string;
      passwordSecretRef: string;
      excludeFromNetrc?: boolean;
    }>;
    options: {
      limit?: Record<string, any>;
      offset?: Record<string, any>;
      sortProperties?: Record<string, any>;
    };
  };
