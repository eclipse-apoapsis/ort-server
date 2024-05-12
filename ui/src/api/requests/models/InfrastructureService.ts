/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

export type InfrastructureService = {
  name: string;
  url: string;
  description?: Record<string, any>;
  usernameSecretRef: string;
  passwordSecretRef: string;
  excludeFromNetrc?: boolean;
};
