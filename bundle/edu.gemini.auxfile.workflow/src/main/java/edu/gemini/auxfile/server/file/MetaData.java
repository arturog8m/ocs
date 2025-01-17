package edu.gemini.auxfile.server.file;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.gemini.auxfile.api.AuxFileException;
import edu.gemini.spModel.core.SPProgramID;
import edu.gemini.spModel.pio.ParamSet;
import edu.gemini.spModel.pio.Pio;
import edu.gemini.spModel.pio.PioFactory;
import edu.gemini.spModel.pio.xml.PioXmlException;
import edu.gemini.spModel.pio.xml.PioXmlFactory;
import edu.gemini.spModel.pio.xml.PioXmlUtil;

public class MetaData {

	private static final Logger LOGGER = Logger.getLogger(MetaData.class.getName());
	
    private static final String PROP_ROOT = "meta";
    private static final String PROP_DESCRIPTION = "description";
	private static final String PROP_CHECKED = "checked";
    
	private final File xml;
	private String description;
	private boolean checked;

	@SuppressWarnings("deprecation")
	public static MetaData forFile(SPProgramID progId, String fileName) throws IOException, AuxFileException {

		// Make sure the dirs exist.
		FileManager.instance().initProgramDir(progId);
		
		File xml = FileManager.instance().getMetaFile(progId, fileName);
		MetaData md = new MetaData(xml);

		// If this is a new MetaData for this auxfile, copy the legacy description if
		// there is one, and delete the legacy description dir/file. Do this here since 
		// we don't pass the progId/fileName info to the ctor.
		if (!xml.exists()) {
			
			// Previous versions had the description in its own file.
			final String DESC_DIR    = "desc";
			final String DESC_SUFFIX = ".desc";
			
			final FileManager r = FileManager.instance();
			final File ddir = new File(r.getProgramDir(progId), DESC_DIR);
			final File dfile = new File(ddir, fileName + DESC_SUFFIX);
			
			if (dfile.exists()) {
				LOGGER.info("Migrating legacy description file for " + fileName + " (" + progId + ")");
				md.setDescription(FileUtil.readString(dfile));
				dfile.delete(); // do this last in case the md update fails
			}
			
		}
		
		return md;
	}

	private MetaData(File xml) throws IOException {
		this.xml = xml;
		read();
	}

	public boolean isChecked() {
		return checked;
	}

	public void setChecked(boolean checked) throws IOException {
		this.checked = checked;
		store();
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) throws IOException {
		this.description = description;
		store();
	}
	
	private void store() throws IOException {
		
		// Create the PIO XML document.
		PioFactory factory = new PioXmlFactory();
		ParamSet node = factory.createParamSet(PROP_ROOT);
		
		// Set the metadata properties
		Pio.addBooleanParam(factory, node, PROP_CHECKED, checked);
		Pio.addParam(factory, node, PROP_DESCRIPTION, description);
		
		// And write out the file.
		try {
			PioXmlUtil.write(node, xml);
		} catch (PioXmlException pxe) {
			LOGGER.log(Level.SEVERE, "Trouble storing meta", pxe);
			IOException ioe = new IOException(pxe.getMessage());
			ioe.initCause(pxe);
			throw ioe;
		}
	}
	
	private void read() throws IOException {
		if (!xml.exists()) return;
		try {

			// Read the PIO XML document.
			ParamSet node = (ParamSet) PioXmlUtil.read(xml);
		
			// Get the metadata properties
			checked = Pio.getBooleanValue(node, PROP_CHECKED, false);
			description = Pio.getValue(node, PROP_DESCRIPTION, null);

		} catch (PioXmlException pxe) {
			LOGGER.log(Level.SEVERE, "Trouble storing meta", pxe);
			IOException ioe = new IOException(pxe.getMessage());
			ioe.initCause(pxe);
			throw ioe;
		}
	}
	
}
