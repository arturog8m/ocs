package edu.gemini.p1monitor.config

import org.osgi.framework.BundleContext
import java.io.File
import javax.mail.internet.InternetAddress
import collection.immutable
import xml.{XML, Node}
import java.util.logging.Logger

case class MonitoredDirectory(name: String,
                              dir: File,
                              username: Option[String],
                              group: Option[String],
                              to: Traversable[InternetAddress],
                              cc: Traversable[InternetAddress],
                              bcc: Traversable[InternetAddress])

class P1MonitorConfig(ctx: BundleContext) {
  val LOG = Logger.getLogger(this.getClass.getName)
  private val CONF_FILE: String = "p1monitor.config"
  private val SMTP_TAG: String = "smtp"
  private val PORT_TAG: String = "org.osgi.service.http.port"
  private val HOST_TAG: String = "p1monitor.host"
  private val TO_TAG: String = "to"
  private val CC_TAG: String = "cc"
  private val BCC_TAG: String = "bcc"
  private val TYPE_TAG: String = "type"
  private val NAME_TAG: String = "name"
  private val DIR_TAG: String = "dir"
  private val USERNAME_TAG: String = "username"
  private val GROUP_TAG: String = "group"

  val xmlConf = {
    val filename = getProp(CONF_FILE)
    LOG.info("P1 Monitor configuration: " + filename)
    XML.load(this.getClass.getResourceAsStream(filename))
  }

  val map: immutable.Map[String, MonitoredDirectory] = getMonitoredDirectories(xmlConf).toMap

  val getSmtp: String = getXmlProp(SMTP_TAG)
  val getPort: String = getProp(PORT_TAG)
  val getHost: String = getProp(HOST_TAG)
  val translations: Map[String, String] = getTranslations

  LOG.info("P1Monitor starting, monitoring %s".format(map.toString()))

  private def getTranslations:Map[String, String] = (xmlConf \\ "translation").map { x =>
      (x \ "from").text -> (x \ "to").text
    }.toMap

  private def getProp(propName: String) = {
    val prop = ctx.getProperty(propName)
    if (prop == null || prop.equals("")) {
      throw new IllegalArgumentException("Property " + propName + " not defined in bundle config")
    }
    prop
  }

  private def getXmlProp(tag: String) = {
    val tags = elementContent(xmlConf, tag)
    if (tags.isEmpty) {
      throw new IllegalArgumentException("Xml file doesn't contain an <" + tag + "> element")
    }
    if (tags.length > 1) {
      LOG.warning("Xml file contains more than one <" + tag + "> tag, using: " + tags.head)
    }
    tags.head
  }

  private def elementContent(root: Node, tag: String): Seq[String] = {
    root.child collect {
      case n: Node if n.label.equals(tag) => n.text
    }
  }

  def getMonitoredDirectories(root: Node): Traversable[(String, MonitoredDirectory)] = {
    val global_to = elementContent(root, TO_TAG)
    val global_cc = elementContent(root, CC_TAG)
    val global_bcc = elementContent(root, BCC_TAG)

    root.child collect {
      case n: Node if n.label.equals(TYPE_TAG) => n
    } map {
      _type => {
        val name = _type.attributes.asAttrMap(NAME_TAG)
        val rootDir = elementContent(_type, DIR_TAG).map(new File(_))
        val username = elementContent(_type, USERNAME_TAG).headOption
        val group = elementContent(_type, GROUP_TAG).headOption
        val to = (elementContent(_type, TO_TAG) ++ global_to).map(new InternetAddress(_))
        val cc = (elementContent(_type, CC_TAG) ++ global_cc).map(new InternetAddress(_))
        val bcc = (elementContent(_type, BCC_TAG) ++ global_bcc).map(new InternetAddress(_))
        (name, MonitoredDirectory(name, rootDir.head, username, group, to, cc, bcc))
      }
    }

  }

  def getDirectories: Traversable[MonitoredDirectory] = map.values

  def getDirectory(name: String) = map(name)

}