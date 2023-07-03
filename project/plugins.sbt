resolvers += MavenRepository("HMRC-open-artefacts-maven2", "https://open.artefacts.tax.service.gov.uk/maven2")
resolvers += Resolver.url("HMRC-open-artefacts-ivy2", url("https://open.artefacts.tax.service.gov.uk/ivy2"))(Resolver.ivyStylePatterns)
resolvers += Resolver.typesafeRepo("releases")

addSbtPlugin("com.typesafe.play"  %  "sbt-plugin"             % "2.8.19")
addSbtPlugin("uk.gov.hmrc"        %  "sbt-auto-build"         % "3.9.0")
addSbtPlugin("uk.gov.hmrc"        %  "sbt-distributables"     % "2.2.0")
addSbtPlugin("org.scalastyle"     %% "scalastyle-sbt-plugin"  % "1.0.0")
// Include the plugin below to display compiler warnings for all files - not just the incrementally compiled ones
addSbtPlugin("com.timushev.sbt"   %  "sbt-rewarn"             % "0.1.3")
