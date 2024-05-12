/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

export type org_eclipse_apoapsis_ortserver_api_v1_model_PagedResponse_org_eclipse_apoapsis_ortserver_api_v1_model_Organization_ =
  {
    data: Array<{
      id: number;
      name: string;
      description?: Record<string, any>;
    }>;
    options: {
      limit?: Record<string, any>;
      offset?: Record<string, any>;
      sortProperties?: Record<string, any>;
    };
  };
