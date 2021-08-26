resolvers += MavenRepository("HMRC-open-artefacts-maven2", "https://open.artefacts.tax.service.gov.uk/maven2")
resolvers += Resolver.url("HMRC-open-artefacts-ivy2", url("https://open.artefacts.tax.service.gov.uk/ivy2"))(Resolver.ivyStylePatterns)
resolvers += Resolver.typesafeRepo("releases")

addSbtPlugin("com.typesafe.play"  %  "sbt-plugin"             % "2.8.7")
addSbtPlugin("uk.gov.hmrc"        %  "sbt-auto-build"         % "3.3.0")
addSbtPlugin("uk.gov.hmrc"        %  "sbt-distributables"     % "2.1.0")
addSbtPlugin("org.scalastyle"     %% "scalastyle-sbt-plugin"  % "1.0.0")
