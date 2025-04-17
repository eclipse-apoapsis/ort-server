# HTTP Request Collection

This directory contains a collection of HTTP requests primarily intended for development and testing of the ORT Server API. 
These examples showcase common operations and can be useful when working with the API during development.
The collection is not exhaustive but provides examples for common operations.
For the complete Server API reference, please see [the API documentation](https://eclipse-apoapsis.github.io/ort-server/api/ort-server-api).

## Environments

The HTTP requests use variables defined in environment files:

- Base configuration: [http-client.env.json](env/http-client.env.json)
- Custom configuration: Create a file called `http-client.private.env.json` to override the default values with your own settings

## Clients

The request format in this collection is compatible with several HTTP clients, including:

- [IntelliJ IDEA Ultimate](https://www.jetbrains.com/help/idea/http-client-in-product-code-editor.html)
- [Kulala.nvim](https://github.com/mistweaverco/kulala.nvim) or [rest.nvim](https://github.com/rest-nvim/rest.nvim) for neovim
- [REST Client for VS Code](https://github.com/Huachao/vscode-restclient)
