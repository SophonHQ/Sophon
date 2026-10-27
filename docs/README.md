# Documentation for Sophon

This folder contains the Sophon documentation, written in AsciiDoc and built with [Antora](https://antora.org/).

The following are the current documentation sources (folders):

- `old-reference-guide`: The Sophon reference guide.
- `dead-letter-queue-guide`: Guide on using dead-letter queues to keep a system operational during failures.
- `deadlines-guide`: Guide on using deadlines to implement time-based constraints.
- `identifier-generation-guide`: Guide covering several considerations regarding identifier generation in Sophon
  applications.
- `message-handler-customization-guide`: Guide on customizing message handlers.
- `meta-annotations-guide`: Guide covering several considerations regarding creating meta-annotations for Sophon
  applications.
- `_reference`: Work-in-progress draft of a new reference guide, which is not part of the build.

The reference guide is derived from the Axon Framework 4 documentation.

## Contributing to the docs

You are welcome to contribute to these docs. Whether you want to fix a typo, or you find something missing, unclear or
open to improvement, please send a pull request to this repository.

## Building and testing the docs locally

To build and explore the docs locally, use the Antora playbook in the `docs/_playbook` folder:

1. Make sure you have Node (an LTS version is preferred) installed.
2. Change to the `docs/_playbook` folder and run `npm install`.
3. Run `npx antora playbook.yaml`. Antora generates the static HTML files under `docs/_playbook/build/site`.
4. Serve the files in `docs/_playbook/build/site` with a local HTTP server, for example by running
   `python3 -m http.server 8070` in that directory.
5. Open `http://localhost:8070` in your browser to navigate the local version of the docs.
