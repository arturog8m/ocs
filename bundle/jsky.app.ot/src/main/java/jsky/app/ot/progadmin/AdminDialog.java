//
// $
//

package jsky.app.ot.progadmin;

import edu.gemini.pot.sp.ISPProgram;
import edu.gemini.pot.spdb.DBIDClashException;
import edu.gemini.pot.spdb.IDBDatabaseService;

import javax.swing.*;

import java.awt.*;

/**
 * A dialog for administering special/expert program properties that are hidden
 * from the common user.
 */
public final class AdminDialog {

    private Frame owner;
    private String title;
    private boolean modal;
    private JOptionPane pane;

    private AdminDialog(Frame owner, String title, boolean modal) {
        this.pane     = new JOptionPane();
        pane.setMessageType(JOptionPane.PLAIN_MESSAGE);
        pane.setOptionType(JOptionPane.OK_CANCEL_OPTION);
        pane.setInitialValue(JOptionPane.OK_OPTION);

        this.owner    = owner;
        this.title    = title;
        this.modal    = modal;
    }

    ISPProgram show(IDBDatabaseService database, ISPProgram prog)  {

        AdminModel model = new AdminModel(prog);

        AdminUI ui = new AdminUI();
        AdminEditor editor = new AdminEditor(ui);
        editor.setModel(model);

        pane.setMessage(ui);

        JDialog dialog = pane.createDialog(owner, title);
        dialog.setModal(modal);
        dialog.setVisible(true);

        Object sel = pane.getValue();
        if (!(sel instanceof Integer) || (JOptionPane.OK_OPTION != (Integer) sel)) {
            return prog;
        }

        model = editor.getModel();

        ISPProgram res = prog;
        try {
            res = model.apply(database, prog);
        } catch (DBIDClashException e) {
            JOptionPane.showMessageDialog(owner,
                String.format("The program id '%s' is already in use, please choose another.", model.getProgramAttrModel().getProgramId()),
                "Duplicate Program ID", JOptionPane.ERROR_MESSAGE);
        }
        return res;
    }

    public static ISPProgram showAdminDialog(Frame owner, IDBDatabaseService database, ISPProgram prog) {
        final AdminDialog ad = new AdminDialog(owner, "Program Admin Settings", true);
        return ad.show(database, prog);
    }
}
