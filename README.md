# ZIO Metrics Connectors

| Project Stage | CI | Release | Snapshot | Discord |
| --- | --- | --- | --- | --- |
| [![Project stage][Stage]][Stage-Page] | ![CI][Badge-CI] | [![Release Artifacts][Badge-SonatypeReleases]][Link-SonatypeReleases] | [![Snapshot Artifacts][Badge-SonatypeSnapshots]][Link-SonatypeSnapshots] | [![Badge-Discord]][Link-Discord] |

# Summary

**ZIO Metrics Connectors** provides pluggable backends into existing Application Performance Monitoring (APM) solutions,
including backends for New Relic, StatsD, DataDog and Prometheus.

TODO: Review sections below

**ZIO Metrics Connectors** allows you to add diagnostics and metrics to any ZIO application.

**ZIO Metrics Connectors** features:

* **Easy Setup** - Add to any ZIO application with only a few lines of code.
* **Backends** - Support for major metrics collection services including Prometheus and StatsD.
* **Zero Dependencies** - No dependencies other than ZIO itself.

See the micro site for more information.

## ZMX in ZIO 1.x becomes ZIO Metrics Connectors

With the release of **ZIO Metrics Connectors** the original repository for ZMX will be archived 
and available read only for future reference. 

The API to capture metrics has moved into ZIO core for ZIO 2.x and later. Therefore ZIO Metrics Connectors 
concentrates on providing the backend connectivity to report the captured metrics. The design 
goal is to have the same instrumentation of the application for any selected backend. 

# Documentation
[ZIO Metrics Connectors Microsite](https://zio.github.io/zio-metrics-connectors/)

# Contributing
[Documentation for contributors](https://zio.github.io/zio-metrics-connectors/docs/about/about_contributing)

## Code of Conduct

See the [Code of Conduct](https://zio.github.io/zio-metrics-connectors/docs/about/about_coc)

## Support

Come chat with us on [![Badge-Discord]][Link-Discord].


# License
[License](LICENSE)

[Badge-SonatypeReleases]: https://img.shields.io/nexus/r/https/oss.sonatype.org/dev.zio/zio-metrics-connectors_2.12.svg "Sonatype Releases"
[Badge-SonatypeSnapshots]: https://img.shields.io/nexus/s/https/oss.sonatype.org/dev.zio/zio-metrics-connectors_2.12.svg "Sonatype Snapshots"
[Badge-Discord]: https://img.shields.io/discord/629491597070827530?logo=discord "chat on discord"
[Link-SonatypeReleases]: https://oss.sonatype.org/content/repositories/releases/dev/zio/zio-metrics-connectors.12/ "Sonatype Releases"
[Link-SonatypeSnapshots]: https://oss.sonatype.org/content/repositories/snapshots/dev/zio/zio-metrics-connectors_2.12/ "Sonatype Snapshots"
[Link-Discord]: https://discord.gg/2ccFBr4 "Discord"
[Badge-CI]: https://github.com/zio/zio-metrics-connectors/workflows/CI/badge.svg
[Stage]: https://img.shields.io/badge/Project%20Stage-Development-yellowgreen.svg
[Stage-Page]: https://github.com/zio/zio/wiki/Project-Stages

