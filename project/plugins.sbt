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
addSbtPlugin("com.typesafe.sbt"   %  "sbt-digest"             % "1.1.4")
addSbtPlugin("com.typesafe.sbt"   %  "sbt-gzip"               % "1.0.2")

// Use the Scalariform plugin to reformat the code
// Note that it currently does not handle implicit parameters well so we need to comment out adding the plugin
// here as it will format the code regardless if it is on the classpath even if the config is removed in build.sbt
//addSbtPlugin("org.scalariform"    %  "sbt-scalariform"        % "1.8.3")

// Use the Scalastyle plugin to check the code adheres to coding standards
addSbtPlugin("org.scalastyle"     %% "scalastyle-sbt-plugin"  % "1.0.0")
