import BuildHelper._

inThisBuild(
  List(
    organization   := "dev.zio",
    homepage       := Some(url("https://zio.dev/zio-metrics-connectors/")),
    licenses       := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers     := List(
      Developer(
        "jdegoes",
        "John De Goes",
        "john@degoes.net",
        url("http://degoes.net"),
      ),
      Developer(
        "softinio",
        "Salar Rahmanian",
        "code@softinio.com",
        url("https://www.softinio.com"),
      ),
    ),
    pgpPassphrase  := sys.env.get("PGP_PASSWORD").map(_.toArray),
    pgpPublicRing  := file("/tmp/public.asc"),
    pgpSecretRing  := file("/tmp/secret.asc"),
    resolvers ++= Seq(
      "s01 Sonatype OSS Snapshots" at "https://s01.oss.sonatype.org/content/repositories/snapshots",
      "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    ),
    scmInfo        := Some(
      ScmInfo(
        url("https://github.com/zio/zio-metrics-connectors/"),
        "scm:git:git@github.com:zio/zio-metrics-connectors.git",
      ),
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
  ),
)

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

lazy val root =
  project
    .in(file("."))
    .settings(publish / skip := true)
    .aggregate(core, statsd, datadog, newrelic, prometheus, micrometer, docs)

lazy val core =
  project
    .in(file("core"))
    .settings(
      stdSettings("zio.metrics.connectors"),
      libraryDependencies ++= Seq(
        "dev.zio" %%% "zio"          % Version.zio,
        "dev.zio" %%% "zio-test"     % Version.zio % Test,
        "dev.zio" %%% "zio-test-sbt" % Version.zio % Test,
      ),
    )
    .settings(buildInfoSettings("zio.metrics.connectors"))
    .enablePlugins(BuildInfoPlugin)

lazy val statsd =
  project
    .in(file("statsd"))
    .settings(stdSettings("zio.metrics.connectors.statsd"))
    .enablePlugins(BuildInfoPlugin)
    .dependsOn(core % "compile->compile;test->test")

lazy val datadog =
  project
    .in(file("datadog"))
    .settings(stdSettings("zio.metrics.connectors.datadog"))
    .settings(buildInfoSettings("zio.metrics.connectors.datadog"))
    .enablePlugins(BuildInfoPlugin)
    .dependsOn(
      core   % "compile->compile;test->test",
      statsd % "compile->compile;test->test",
    )

lazy val newrelic =
  project
    .in(file("newrelic"))
    .settings(
      stdSettings("zio.metrics.connectors.newrelic"),
      libraryDependencies ++= Seq(
        "dev.zio"  %% "zio-http" % Version.zioHttp,
        "dev.zio" %%% "zio-json" % Version.zioJson,
      ),
    )
    .settings(buildInfoSettings("zio.metrics.connectors.newrelic"))
    .enablePlugins(BuildInfoPlugin)
    .dependsOn(core % "compile->compile;test->test")

lazy val prometheus =
  project
    .in(file("prometheus"))
    .settings(stdSettings("zio.metrics.connectors.prometheus"))
    .settings(buildInfoSettings("zio.metrics.connectors.prometheus"))
    .enablePlugins(BuildInfoPlugin)
    .dependsOn(core % "compile->compile;test->test")

lazy val sampleApp =
  project
    .in(file("sample-app"))
    .settings(
      run / fork := true,
      run / javaOptions += "-Djava.net.preferIPv4Stack=true",
      libraryDependencies ++= Seq(
        "dev.zio"  %% "zio-http" % Version.zioHttp,
        "dev.zio" %%% "zio-json" % Version.zioJson,
      ),
    )
    .dependsOn(statsd, prometheus)

lazy val micrometer =
  project
    .in(file("micrometer"))
    .settings(
      stdSettings("zio.metrics.connectors.micrometer"),
      libraryDependencies ++= Seq(
        "io.micrometer" % "micrometer-core"                % Version.micrometer,
        "io.micrometer" % "micrometer-registry-prometheus" % Version.micrometer % Test,
      ),
    )
    .settings(buildInfoSettings("zio.metrics.connectors.micrometer"))
    .enablePlugins(BuildInfoPlugin)
    .dependsOn(core % "compile->compile;test->test")

lazy val docs = project
  .in(file("zio-metrics-connectors-docs"))
  .settings(
    moduleName                                 := "zio-metrics-connectors-docs",
    scalacOptions -= "-Yno-imports",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio"      % Version.zio,
      "dev.zio" %% "zio-http" % Version.zioHttp,
    ),
    projectName                                := "ZIO Metrics Connectors",
    mainModuleName                             := (core / moduleName).value,
    projectStage                               := ProjectStage.Development,
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(core),
    docsPublishBranch                          := "zio/series/2.x",
  )
  .dependsOn(core)
  .enablePlugins(WebsitePlugin)
