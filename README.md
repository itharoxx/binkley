binkley's BLOG
==============

<http://binkley.blogspot.com>

This software is in the Public Domain.  Please see [LICENSE.md](LICENSE.md).

Current released version is 0.4.  [View javadoc](//binkley.github.io/binkley/).

## Modules

* [Convert](convert/) - Inverse of `toString()`
* [Guice](guice/) - Sample Guice modules and helper code
* [Logging](logging/) - Small logging improvements and OSI logback configuration
* [Mixin](mixin/) - Mixins for Java via JDK proxies and method handles
* [Spring](spring/) - Examples with Spring
* [Testing](testing/) - Small testing improvements
* [Utility](util/) - `Bug`, `CheckedStream` and friends
* [Value type](value-type/) - Java annotation and processor for value types
* [Xio](xio/) - Pulling out interfaces from JDK I/O
* [Xproperties](xprops/) - Extended Java properties

## Changes

### 0.4

* Dropped finance module: use JSR 354.
* Fixed issues with support loggers.  OSI logging is no longer beta.
* Default OSI logging level is INFO, not WARN.
* Added support for ANSI codes in logging via OSI and other improvements.
* OSI logging requires a minimum of Java 7 or higher.
* Various converters reorganized.
* Checked stream is Java 8 streams with checked exceptions.
