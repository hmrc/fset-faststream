credentials += Credentials(Path.userHome / ".sbt" / ".credentials")

resolvers += Resolver.url("hmrc-sbt-plugin-releases",
  url("https://dl.bintray.com/hmrc/sbt-plugin-releases"))(Resolver.ivyStylePatterns)

resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/"

//addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.6.0")

addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.7.0")

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.3.10")

addSbtPlugin("com.typesafe.sbt" % "sbt-digest" % "1.0.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-gzip" % "1.0.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-auto-build" % "1.0.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-distributables" % "0.11.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-git-versioning" % "0.8.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-settings" % "3.2.0")

//libraryDependencies += "org.scalariform" %% "scalariform" % "0.1.8"