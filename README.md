# Sophon

Sophon is a Java framework for building monoliths and microservices alike using tactical DDD, CQRS, and Event Sourcing.

This project is a fork of Axon Framework 4, originally developed by AxonIQ B.V. and contributors. Not affiliated
with or endorsed by AxonIQ.

## Why a fork

Developers of Axon 5 made some questionable choices that created the need for a maintained fork of Axon 4.
These decisions include:

* Dropped support for Axon 4
* No ability to opt-out from Event Sourcing
* Futures and reactive streams as first-class citizens in a world where virtual threads are already generally available
* General greedy corporate practices of moving open source features into proprietary AxonIq Framework (Sagas to
  Workflows etc.)

## Versioning

Sophon starts with a fresh versioning scheme. The 1.0.0 version is a fork of Axon Framework 4.13.2 with certain
modifications included:

1. Changed `groupId`, `artifactId` and packages to reflect new name.
2. Removed code associated with proprietary products (Axon Server, AxonIQ Console, Update Checker)
3. Migrated to jsr305 and JetBrains annotations to JSpecify
4. `legacy` module deleted

## Feature requests and issue reporting

Use GitHub's [issue tracking system](https://github.com/SophonHQ/Sophon/issues) for new feature requests, enhancements,
and bugs.
Before filing an issue, please verify that it's not already reported by someone else.
Furthermore, make sure you are adding the issue to the correct repository!

When filing bugs:

* A description of your setup and what's happening helps us figure out what the issue might be.
* Do not forget to provide the versions of the Axon products you're using, as well as the language and version.
* If possible, share a stack trace.
  Please use Markdown semantics by starting and ending the trace with three backticks (```).

When filing a feature or enhancement:

* Please provide a description of the feature or enhancement at hand.
  Adding why you think this would be beneficial is also a great help to us.
* (Pseudo-)Code snippets showing what it might look like will help us understand your suggestion better.
  Similarly as with bugs, please use Markdown semantics for code snippets, starting and ending with three
  backticks (```).
* If you have any thoughts on where to plug this into the framework, that would be very helpful too.
* Feel free to provide a Pull Request as well.