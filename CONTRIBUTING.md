# Contribution Guidelines

Thank you for your interest in contributing to Sophon. To make sure using Sophon is a smooth experience for everybody,
we've set up a number of guidelines to follow.

There are different ways in which you can contribute to the framework:

1. You can report any bugs, feature requests or ideas about improvements on
   our [issue page](https://github.com/SophonHQ/Sophon/issues/new/choose). All ideas are welcome. Please be as exact as
   possible when reporting bugs. This will help us reproduce and thus solve the problem faster.
2. If you have created a component for your own application that you think might be useful to include in the framework,
   send us a pull request. We will evaluate it and try to fit it in the framework. Please make sure code is properly
   documented using JavaDoc. This helps us to understand what is going on.

## Code Contributions

If you're contributing code, please take care of the following:

### Licensing

Sophon is licensed under the [Apache License, Version 2.0](LICENSE.txt). By submitting a contribution, you agree that it
is licensed under the same terms, as described in section 5 of the license.

### Copyright Headers

Every source file must start with a license header:

* New files get the Sophon header, `Copyright (c) <year>. SophonHQ`, followed by the Apache License notice.
* Files derived from Axon Framework keep their original `Copyright (c) <years>. Axon Framework` line. Never change or
  remove it. The `Modifications Copyright (c) 2026. SophonHQ` line below it marks the file as modified, as
  required by section 4 of the license; it doesn't need updating.

If you're using IntelliJ IDEA, import [copyright-template.xml](copyright-template.xml) under "Settings -> Editor ->
Copyright -> Copyright Profiles" and make it the default profile. It only replaces headers that belong solely to Sophon,
so "Update Copyright" leaves the headers of files derived from Axon Framework untouched.

### Code Style

We're trying very hard to maintain a consistent style of coding throughout the code base. Think of things like indenting
using 4 spaces, putting opening brackets (the '{') on the same line and putting proper JavaDoc on all non-private
members.

If you're using IntelliJ IDEA, you can import the code style definition from
[sophon_code_style.xml](sophon_code_style.xml) under "Settings -> Code Style -> Scheme -> Import Scheme". Doing so
should make the code style selectable immediately.
