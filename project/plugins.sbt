resolvers ++= Seq(
  Resolver.url("HMRC Sbt Plugin Releases", url("https://dl.bintray.com/hmrc/sbt-plugin-releases"))(Resolver.ivyStylePatterns),
  "HMRC Releases" at "https://dl.bintray.com/hmrc/releases",
  "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/"
)

addSbtPlugin("uk.gov.hmrc"        %  "sbt-artifactory"        % "0.13.0")
addSbtPlugin("uk.gov.hmrc"        %  "sbt-auto-build"         % "1.13.0")
addSbtPlugin("uk.gov.hmrc"        %  "sbt-distributables"     % "1.1.0")
addSbtPlugin("uk.gov.hmrc"        %  "sbt-git-versioning"     % "1.15.0")
addSbtPlugin("uk.gov.hmrc"        %  "sbt-settings"           % "3.8.0")
addSbtPlugin("com.typesafe.play"  %  "sbt-plugin"             % "2.7.4")
addSbtPlugin("org.scalastyle"     %% "scalastyle-sbt-plugin"  % "0.8.0")
