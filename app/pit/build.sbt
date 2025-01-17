
import OcsKeys._
import edu.gemini.osgi.tools.Version
import edu.gemini.osgi.tools.app.{ Configuration => AppConfig, _ }
import edu.gemini.osgi.tools.app.Configuration.Distribution.{ Test => TestDistro, _ }

ocsAppSettings

// Application project for PIT
ocsAppManifest := {
  val pv = pitVersion.value.toBundleVersion
  Application(
    id = "pit",
    name = "PIT",
    version = pitVersion.value.toString,
    useShortVersion = true,
    configs = List(
      common(pv),
        dev(pv),
        mac(pv),
        linux(pv),
        windows(pv)
    )
  )
}

// COMMON
def common(pv: Version) = AppConfig(
  id = "common",
  vmargs = List(
    "-Xmx512M",
    "-Dedu.gemini.ui.workspace.impl.Workspace.fonts.shrunk=true",
    "-Dfile.encoding=UTF-8"
  ),
  props = Map(
    "org.osgi.framework.storage.clean"        -> "onFirstInit",
    "org.osgi.framework.startlevel.beginning" -> "100",
    "org.osgi.framework.bootdelegation"       -> "*",
    "edu.gemini.pit.test"                     -> "true",
    "edu.gemini.ags.host"                     -> "gnodb.gemini.edu",
    "edu.gemini.ags.port"                     -> "8443"
  ),
  // log = Some("%a/log/qpt.%u.%g.log"),
  bundles = List(
    BundleSpec("edu.gemini.osgi.main",         Version(4, 2, 1)),
    BundleSpec("edu.gemini.pit",               pv),
    BundleSpec("slf4j.api",                    Version(1, 6, 4)),
    BundleSpec("slf4j.jdk14",                  Version(1, 6, 4)),
    BundleSpec("org.scala-lang.scala-actors",  Version(2, 10, 1)),
    BundleSpec("org.scala-lang.scala-reflect", Version(2, 10, 1)),
    BundleSpec("org.scala-lang.scala-swing",   Version(2, 0, 0)),
    BundleSpec("org.apache.commons.logging",   Version(1, 1, 0))
  )
) extending List()

def dev(pv: Version) = AppConfig(
  id = "test",
  distribution = List(TestDistro),
  bundles = List(
    BundleSpec("org.apache.felix.gogo.runtime", Version(0, 10, 0)),
    BundleSpec("org.apache.felix.gogo.command", Version(0, 12, 0)),
    BundleSpec("org.apache.felix.gogo.shell",   Version(0, 10, 0))
  )
) extending List(common(pv))

def mac(pv: Version) = AppConfig(
  id = "mac",
  distribution = List(MacOS),
  icon=Some(file("app/pit/dist/MacOS/PIT.icns")),
  props = Map(
    "org.osgi.framework.storage" -> "$CachesDirectory/edu.gemini.PIT/felix-cache"
  )
) extending List(common(pv))

def linux(pv: Version) = AppConfig(
  id = "linux",
  distribution = List(Linux32, Linux64),
  props = Map(
    "org.osgi.framework.storage" -> "${user.home}/.edu.gemini.PIT/felix-cache"
  )
) extending List(common(pv))

def windows(pv: Version) = AppConfig(
  id = "windows",
  distribution = List(Windows),
  icon=Some(file("app/pit/dist/Windows/PIT.ico")),
  props = Map(
    "org.osgi.framework.storage" -> "${user.home}/AppData/Local/edu.gemini.PIT/felix-cache"
  )
) extending List(common(pv))


