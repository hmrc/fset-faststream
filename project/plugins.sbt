resolvers += MavenRepository("HMRC-open-artefacts-maven2", "https://open.artefacts.tax.service.gov.uk/maven2")
resolvers += Resolver.url("HMRC-open-artefacts-ivy2", url("https://open.artefacts.tax.service.gov.uk/ivy2"))(Resolver.ivyStylePatterns)
resolvers += Resolver.typesafeRepo("releases")

addSbtPlugin("com.typesafe.play"  %  "sbt-plugin"             % "2.8.8")
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
