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

lazy val commonSettings = Seq()

lazy val core =
  project
    .in(file("core"))
    .settings(
      run / fork             := true,
      Test / run / javaOptions += "-Djava.net.preferIPv4Stack=true",
      Test / run / mainClass := Some("zio.sample.SampleApp"),
      cancelable             := true,
      stdSettings("zio.metrics.connectors"),
      libraryDependencies ++= Seq(
        "dev.zio" %%% "zio"          % Version.zio,
        "dev.zio" %%% "zio-json"     % Version.zioJson,
        "dev.zio" %%% "zio-streams"  % Version.zio,
        "dev.zio"  %% "zio-http"     % Version.zioHttp,
        "dev.zio" %%% "zio-test"     % Version.zio % Test,
        "dev.zio" %%% "zio-test-sbt" % Version.zio % Test,
      ),
    )
    .settings(buildInfoSettings("zio.metrics.connectors"))
    .enablePlugins(BuildInfoPlugin)

lazy val docs = project
  .in(file("zio-metrics-connectors-docs"))
  .settings(
    commonSettings,
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
    docsPublishBranch                          := "series/2.x",
  )
  .dependsOn(core)
  .enablePlugins(WebsitePlugin)
