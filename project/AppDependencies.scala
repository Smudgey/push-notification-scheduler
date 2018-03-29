import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "bootstrap-play-25" % "1.5.0",
    "uk.gov.hmrc" %% "domain" % "5.0.0",
    "uk.gov.hmrc" %% "reactive-circuit-breaker" % "3.2.0",
    "uk.gov.hmrc" %% "play-scheduling" % "4.1.0"
  )

  def test(scope: String = "test,it") = Seq(
    ws excludeAll ExclusionRule("org.apache.httpcomponents"),
    "uk.gov.hmrc" %% "hmrctest" % "3.0.0" % scope,
    "org.mockito" % "mockito-core" % "2.11.0" % scope,
    "com.typesafe.akka" %% "akka-testkit" % "2.5.6" % scope,
    "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
    "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.1" % scope
  )


  def apply() = compile ++ test("test") ++ test("it")

}
