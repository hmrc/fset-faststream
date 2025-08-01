resolvers += MavenRepository("HMRC-open-artefacts-maven2", "https://open.artefacts.tax.service.gov.uk/maven2")
resolvers += Resolver.url("HMRC-open-artefacts-ivy2", url("https://open.artefacts.tax.service.gov.uk/ivy2"))(Resolver.ivyStylePatterns)
resolvers += Resolver.typesafeRepo("releases")

addSbtPlugin("org.playframework"  %  "sbt-plugin"             % "3.0.8")
addSbtPlugin("uk.gov.hmrc"        %  "sbt-auto-build"         % "3.24.0")
addSbtPlugin("uk.gov.hmrc"        %  "sbt-distributables"     % "2.6.0")
addSbtPlugin("org.scalastyle"     %% "scalastyle-sbt-plugin"  % "1.0.0" exclude("org.scala-lang.modules", "scala-xml_2.12"))
// Include the plugin below to display compiler warnings for all files - not just the incrementally compiled ones
addSbtPlugin("com.timushev.sbt"   %  "sbt-rewarn"             % "0.1.3")

addDependencyTreePlugin
