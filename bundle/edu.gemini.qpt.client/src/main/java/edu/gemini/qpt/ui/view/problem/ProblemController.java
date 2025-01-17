package edu.gemini.qpt.ui.view.problem;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.Set;

import edu.gemini.qpt.core.Marker;
import edu.gemini.qpt.core.Schedule;
import edu.gemini.qpt.core.util.MarkerManager;
import edu.gemini.ui.gface.GTableController;
import edu.gemini.ui.gface.GViewer;

public class ProblemController implements GTableController<Schedule, Marker, ProblemAttribute>, PropertyChangeListener {

	private GViewer<Schedule, Marker> viewer;
	private MarkerManager manager;
	private Marker[] markers;
	
	public Object getSubElement(Marker element, ProblemAttribute subElement) {
		if (element != null) {
			switch (subElement) {
			case Description: return element.getText();
			case Resource: return element.getTarget();
			case Severity: return element.getSeverity();
			}
		}
		return null;
	}

	public Marker getElementAt(int row) {		
		return row < markers.length ? markers[row] : null;
	}

	public int getElementCount() {
		return markers == null ? 0 : markers.length;
	}

	public void modelChanged(GViewer<Schedule, Marker> viewer, Schedule oldModel, Schedule newModel) {
		this.viewer = viewer;
		if (manager != null) manager.removePropertyChangeListener(this);
		manager = newModel == null ? null : newModel.getMarkerManager();
		if (manager != null) manager.addPropertyChangeListener(this);
		fetchMarkers();
	}

	public void propertyChange(PropertyChangeEvent evt) {
		fetchMarkers();
		viewer.refresh();
	}

	private synchronized void fetchMarkers() {
		Set<Marker> set = manager == null ? Collections.<Marker>emptySet() : manager.getMarkers();
		markers = set.toArray(new Marker[set.size()]);
	}
	
}
