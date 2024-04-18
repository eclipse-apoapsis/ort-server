/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

export type CreateInfrastructureService = {
    name: string;
    url: string;
    description?: Record<string, any>;
    usernameSecretRef: string;
    passwordSecretRef: string;
    excludeFromNetrc?: boolean;
};

