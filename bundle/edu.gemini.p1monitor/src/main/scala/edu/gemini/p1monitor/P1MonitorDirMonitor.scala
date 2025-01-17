package edu.gemini.p1monitor

import config.P1MonitorConfig
import java.io.{FileInputStream, FileOutputStream, File}
import java.util.logging.{Level, Logger}
import edu.gemini.model.p1.pdf.P1PDF
import edu.gemini.model.p1.immutable.{Proposal, ProposalIo}

class P1MonitorDirMonitor(cfg: P1MonitorConfig) extends DirListener {
  val LOG = Logger.getLogger(this.getClass.getName)
  val mailer: P1MonitorMailer = new P1MonitorMailer(cfg)

  //One directory scanner per directory
  val dirScanner = cfg.getDirectories map {
    monDir => new DirScanner(monDir)
  }

  def startMonitoring() {
    dirScanner.foreach {
      _.startMonitoring(this)
    }

  }

  def stopMonitoring() {
    dirScanner.foreach {
      _.stopMonitoring()
    }

  }

  def dirChanged(evt: DirEvent) {
    // Make a original dir in not existing
    val originalsDir = new File(evt.dir.dir, "original")
    if (!originalsDir.exists()) {
      originalsDir.mkdirs()
    }

    def writeUpdated(attachment: File, proposal: Proposal, f: File) {
      // Use just the name to avoid hardcoding the file
      val attachedFileName = attachment.getName
      LOG.info(s"Attachment file changed to $attachedFileName")
      ProposalIo.write(proposal.copy(meta = proposal.meta.copy(attachment = Some(new File(attachedFileName)))), f)
    }

    def updateAttachmentLink(newPDFile: Option[File], f: File) = {
      // If we have a PDF we must replace the attachment name
      newPDFile.foreach {
        case p =>
          // This sounds like a good idea, let Proposal to do the IO, but this will need some help to avoid writing
          // the absolute path of the file
          for {
            conversion <- ProposalIo.readAndConvert(f)
          } yield writeUpdated(p, conversion.proposal, f)
      }
    }

    def copyAndProcessFile(xml: File):Iterable[ProposalFileGroup] =
      cfg.translations.collect {
        case (s:String, d:String) if xml.getName.startsWith(s) => (s, d)
      }.flatMap {t =>
        val pdfName = xml.getName.replaceAll(".xml", ".pdf")
        val pdfOpt = evt.newFiles.find(_.getName.equalsIgnoreCase(pdfName))
        val replacedName = new File(xml.getParentFile.getAbsolutePath, xml.getName.replaceAll(t._1, t._2))

        val newXMLFile = copyFile(xml, replacedName)
        val newPDFile = pdfOpt.flatMap {
          pdf => copyFile(pdf, new File(pdf.getParentFile.getAbsolutePath, pdf.getName.replaceAll(t._1, t._2)))
        }

        val fg:Option[ProposalFileGroup] = newXMLFile.flatMap {
          case f:File =>
            updateAttachmentLink(newPDFile, f)

            val r:Option[ProposalFileGroup] = try {
              val summaryFile = new File(f.getAbsolutePath.substring(0, f.getAbsolutePath.length - 4) + "_summary.pdf")
              LOG.info("Build summary report of %s at %s".format(f, summaryFile))
              P1PDF.createFromFile(f, P1PDF.DEFAULT, summaryFile)
              Some(new ProposalFileGroup(Some(f), newPDFile, Some(summaryFile)))
            } catch {
              case ex: Exception =>
                LOG.log(Level.SEVERE, "Problem processing file " + xml.getName, ex)
                None
            }
            r
        }

        // Move originals out of the way
        xml.renameTo(new File(originalsDir, xml.getName))
        pdfOpt.foreach {
          f => f.renameTo(new File(originalsDir, f.getName))
        }
        fg
      }

    //For every XML file, try to get the matching PDF and create the PDF summary, if something fails, just continue
    //with just the XML
    val fileGroups:Traversable[ProposalFileGroup] = evt.newFiles.collect {
      case xml:File if xml.getName.endsWith(".xml") => copyAndProcessFile(xml)
    }.flatten

    //notify of all the new proposals
    fileGroups.foreach {
      mailer.notify(evt.dir.name, _)
    }

  }

  def copyFile(src:File, dest:File):Option[File] = {
    require (src != null)
    require (dest != null)
    src match {
      case s if src.exists() && src != dest =>
        LOG.info(s"Copy file $src to $dest")
        new FileOutputStream(dest).getChannel.transferFrom(new FileInputStream(src).getChannel, 0, Long.MaxValue)
        Some(dest)
      case s if src.exists() && src == dest =>
        LOG.info(s"Destination for $src, $dest already in place")
        Some(s)
      case _                                =>
        LOG.info(s"It seems src does not exist: $src")
        None
    }
  }


}

case class ProposalFileGroup(xml: Option[File], pdf: Option[File], summary: Option[File])
