package jsky.app.ot.viewer.action;

import edu.gemini.util.security.auth.ui.AuthDialog$;
import jsky.app.ot.OT;
import jsky.app.ot.viewer.SPViewer;
import jsky.util.gui.DialogUtil;

import java.awt.event.ActionEvent;

/**
* Created with IntelliJ IDEA.
* User: rnorris
* Date: 1/17/13
* Time: 1:33 PM
* To change this template use File | Settings | File Templates.
*/
public class OpenKeyManagerAction extends AbstractViewerAction {

    final String detailText = "Database keys allow you to access programs and OT features.";


    public OpenKeyManagerAction(final SPViewer viewer) {
        super(viewer, "Manage Keys...");
    }

    public void actionPerformed(ActionEvent e) {
        try {

            // Let the user muck around with keys. This can change the content
            // of our current Subject, affecting our privileges.
            AuthDialog$.MODULE$.openWithDetailText(OT.getKeyChain(), detailText, viewer);
            if (viewer != null) {
                viewer.authListener.propertyChange(null); // force redraw of menu and editor
            }

        } catch (Exception ex) {
            DialogUtil.error(ex);
        }
    }

    @Override
    public boolean computeEnabledState() {
        return true;
    }
}
