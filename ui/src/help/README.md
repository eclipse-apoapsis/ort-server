# Inline help

Each Markdown file in this directory, except this README, is a help document about one concept of
ORT Server, such as infrastructure services or secrets. A `HelpButton` next to any UI element
opens its document in the help panel, scrolled to the section about that element.

## Writing a help document

- **One document per concept.** Name the file after the concept as the navigation of the UI names
  it, in kebab case: the "Infrastructure Services" page gets `infrastructure-services.md`.
- **Start with a level one heading.** It is the title of the panel, so write
  `# Infrastructure Services` on the first line. Follow it with an overview of the concept; a help
  button without a section opens the document there.
- **Give each UI element that needs explaining its own section** with a heading that ends in an
  explicit ID: `## URL Prefix {#url}`. Help buttons and links point at the ID, so the heading can
  be reworded without changing them. Let the ID name the element rather than repeat the heading.
  For a form field, use the property of the field in the API model, in kebab case, which is also
  the `name` of the form field in nearly every form: `name='credentialsTypes'` becomes
  `{#credentials-types}`. A heading without an ID cannot be pointed at.
- **Use the name that the UI shows for the element as the heading,** such as the label of a form
  field. The help button takes its name for screen readers from the heading, so "URL Prefix" makes
  the button "Help: URL Prefix".
- **One section per element, wherever it appears.** The create and edit pages at every hierarchy
  level point at the same section. Different elements get different sections.
- **Make each section readable on its own,** as the panel opens in the middle of the document.
  Background shared by several sections belongs in the overview.

## Updating the index

`index.gen.ts` lists the documents with their titles and sections. It is generated, so do not edit
it; run `pnpm generate:help` after adding a document or changing its headings. `pnpm dev` does
this on its own whenever a document is added, changed or deleted, and prints the reason in its
terminal when a document cannot be indexed, for example because it has no level one heading. A
unit test fails when the index is out of date.

## Adding a help button

Place `<HelpButton topic='<document>#<section ID>' />` next to the UI element it explains. For a
form field, put it next to the label, outside the `<label>` element. Leave out `#<section ID>` to open the document at the top, for example next to the page
heading. TypeScript checks the topic against the index, so a misspelt document or section does not
compile.
