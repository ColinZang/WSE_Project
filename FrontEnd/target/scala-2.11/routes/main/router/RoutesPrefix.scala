
// @GENERATOR:play-routes-compiler
// @SOURCE:/Users/BINLI/IdeaProjects/play-java-intro/conf/routes
// @DATE:Tue Apr 12 22:47:45 EDT 2016


package router {
  object RoutesPrefix {
    private var _prefix: String = "/"
    def setPrefix(p: String): Unit = {
      _prefix = p
    }
    def prefix: String = _prefix
    val byNamePrefix: Function0[String] = { () => prefix }
  }
}
