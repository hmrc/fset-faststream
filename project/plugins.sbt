resolvers ++= Seq(
  Resolver.url("HMRC Sbt Plugin Releases", url("https://dl.bintray.com/hmrc/sbt-plugin-releases"))(Resolver.ivyStylePatterns),
  "HMRC Releases" at "https://dl.bintray.com/hmrc/releases",
  "Typesafe Releases" at "https://repo.typesafe.com/typesafe/releases/"
)

addSbtPlugin("com.typesafe.play"  %  "sbt-plugin"             % "2.8.7")
addSbtPlugin("uk.gov.hmrc"        %  "sbt-artifactory"        % "1.13.0")
addSbtPlugin("uk.gov.hmrc"        %  "sbt-auto-build"         % "2.13.0")
addSbtPlugin("uk.gov.hmrc"        %  "sbt-distributables"     % "2.1.0")
addSbtPlugin("uk.gov.hmrc"        %  "sbt-git-versioning"     % "2.2.0")
addSbtPlugin("uk.gov.hmrc"        %  "sbt-settings"           % "4.7.0")
addSbtPlugin("org.scalastyle"     %% "scalastyle-sbt-plugin"  % "1.0.0")
