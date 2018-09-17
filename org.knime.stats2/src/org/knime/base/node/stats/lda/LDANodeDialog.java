package org.knime.base.node.stats.lda;

import java.awt.Color;
import java.awt.Dimension;

import javax.swing.BoxLayout;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JSpinner.DefaultEditor;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.apache.commons.lang3.text.WordUtils;
import org.knime.core.data.DataColumnDomain;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.NominalValue;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.defaultnodesettings.DialogComponent;
import org.knime.core.node.defaultnodesettings.DialogComponentColumnFilter2;
import org.knime.core.node.defaultnodesettings.DialogComponentColumnNameSelection;
import org.knime.core.node.defaultnodesettings.DialogComponentLabel;
import org.knime.core.node.defaultnodesettings.DialogComponentNumber;
import org.knime.core.node.defaultnodesettings.SettingsModelColumnFilter2;
import org.knime.core.node.defaultnodesettings.SettingsModelInteger;
import org.knime.core.node.defaultnodesettings.SettingsModelString;

/**
 * <code>NodeDialog</code> for the "Linear Discriminant Analysis" Node.
 *
 * @author Alexander Fillbrunn
 */
public class LDANodeDialog extends NodeDialogPane {

    private final JPanel m_panel;

    private final DialogComponentNumber m_dimensionComponent;

    private final DialogComponentColumnNameSelection m_classColComponent;

    private final DialogComponentColumnFilter2 m_usedColsComponent;

    private int m_maximumDim;

    private int m_selectedColumns;

    private int m_selectedClasses;

    private DataTableSpec[] m_lastSpecs;

    private final DialogComponentLabel m_tooHighDimLabel;

    private final DialogComponent m_maxDimZeroLabel;

    private final SettingsModelInteger m_dimensionModel;

    private final SettingsModelString m_classColModel;

    private final SettingsModelColumnFilter2 m_usedColsModel;

    private final SpinnerCL m_spinnerCL;

    private class SpinnerCL implements ChangeListener {
        int m_previousDim = m_dimensionModel.getIntValue();

        @Override
        public void stateChanged(final ChangeEvent e) {
            int validDim = m_dimensionModel.getIntValue();
            validDim = Math.min(validDim, m_maximumDim);
            validDim = Math.max(validDim, 1);

            if (validDim != m_dimensionModel.getIntValue()) {
                // this calls the change listener again
                m_dimensionModel.setIntValue(validDim);

                // update warnings only if the Spinner was invalid to avoid a little overhead
                if (m_previousDim > m_maximumDim) {
                    updateSpinner();
                    updateWarnings();
                }
                m_previousDim = validDim;
            }
        }

        public void setPreviousDim(final int prevDim) {
            m_previousDim = prevDim;
        }
    }

    /**
     * New pane for configuring the node.
     */
    @SuppressWarnings("unchecked")
    protected LDANodeDialog() {
        m_dimensionModel = LDANodeModel.createKSettingsModel();
        m_spinnerCL = new SpinnerCL();
        m_dimensionModel.addChangeListener(m_spinnerCL);
        m_dimensionComponent = new DialogComponentNumber(m_dimensionModel, "Dimensions", 1, 5);

        m_classColModel = LDANodeModel.createClassColSettingsModel();
        m_classColModel.addChangeListener(l -> updateSettings());
        m_classColComponent =
                new DialogComponentColumnNameSelection(m_classColModel, "Class column", 0, NominalValue.class);
        // Smaller size for long names, but with tooltips.
        m_classColComponent.getComponentPanel().getComponent(1).setPreferredSize(new Dimension(260, 25));

        m_usedColsModel = LDANodeModel.createUsedColsSettingsModel();
        m_usedColsModel.addChangeListener(l -> updateSettings());
        m_usedColsComponent = new DialogComponentColumnFilter2(m_usedColsModel, 0);

        // These messages will be made more precise once shown.
        m_maxDimZeroLabel = new DialogComponentLabel(wrapText(""));
        m_maxDimZeroLabel.getComponentPanel().getComponent(0).setForeground(Color.red);
        m_tooHighDimLabel = new DialogComponentLabel(wrapText(""));
        m_tooHighDimLabel.getComponentPanel().getComponent(0).setForeground(Color.red);

        m_panel = new JPanel();
        final BoxLayout bl = new BoxLayout(m_panel, 1);
        m_panel.setLayout(bl);
        m_panel.add(m_dimensionComponent.getComponentPanel());
        m_panel.add(m_classColComponent.getComponentPanel());
        m_panel.add(m_usedColsComponent.getComponentPanel());
        m_panel.add(m_maxDimZeroLabel.getComponentPanel());
        m_panel.add(m_tooHighDimLabel.getComponentPanel());
        addTab("Settings", m_panel);

        updateSettings();
    }

    private static String wrapText(final String text) {
        return "<html>" + WordUtils.wrap(text, 75, "<br/>", true) + "</html>";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        validateSettings();

        m_classColComponent.saveSettingsTo(settings);
        m_usedColsComponent.saveSettingsTo(settings);
        m_dimensionComponent.saveSettingsTo(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final DataTableSpec[] specs)
            throws NotConfigurableException {
        m_lastSpecs = specs; // Needed to get the number of classes.

        m_classColComponent.loadSettingsFrom(settings, specs);
        m_usedColsComponent.loadSettingsFrom(settings, specs);

        // for the case of a too large maxDim - do not fire the changeListener, else the dimension will be reset
        m_dimensionModel.removeChangeListener(m_spinnerCL);
        m_dimensionComponent.loadSettingsFrom(settings, specs);
        m_spinnerCL.setPreviousDim(m_dimensionModel.getIntValue());
        m_dimensionModel.addChangeListener(m_spinnerCL);

        updateSettings();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onOpen() {
        updateSettings();
    }

    private String createMaxDimZeroWarning() {
        final StringBuilder stb = new StringBuilder("The maximum allowed dimension is 0!");
        if ((m_selectedClasses - 1) <= 0) {
            if (m_selectedClasses == -1) {
                stb.append(" There are more than 60 distinct values in the selected class column \""
                        + m_classColComponent.getSelected() + "\", such that the"
                        + " columns domain was not calculated by default. Please use the \"Domain Calculator\" node.");
            } else if (m_selectedClasses == 0) {
                stb.append(
                    " There are only missing values in the selected class column \"" + m_classColComponent.getSelected()
                    + "\". Please provide a class column with at least" + " two distinct values.");
            } else if (m_selectedClasses == 1) {
                stb.append(" There is only one distinct value in the selected class column \""
                        + m_classColComponent.getSelected()
                        + "\". Please provide a class column with at least two distinct values.");
            }
        }

        if (m_selectedColumns == 0) {
            if ((m_selectedClasses - 1) <= 0) {
                stb.append(" Also, t");
            } else {
                stb.append(" T");
            }
            stb.append("here are no columns selected. Please select at least one.");
        }
        return stb.toString();
    }

    private String createTooHighDimWarning() {
        final int currentDim = m_dimensionModel.getIntValue();

        final StringBuilder stb = new StringBuilder("The current number of selected dimensions (" + currentDim
            + ") is higher than the maximum allowed value of " + m_maximumDim + "!");
        if (m_maximumDim == (m_selectedClasses - 1)) {
            stb.append(" The selected class column only features " + m_selectedClasses + " distinct value"
                    + (m_selectedClasses == 1 ? "" : "s"));
            if (m_maximumDim != m_selectedColumns) {
                stb.append(".");
            }
        }

        if (m_maximumDim == m_selectedColumns) {
            if (m_maximumDim == (m_selectedClasses - 1)) {
                stb.append(" and o");
            } else {
                stb.append(" O");
            }
            stb.append(
                "nly " + m_selectedColumns + " column" + (m_selectedColumns == 1 ? " is" : "s are") + " selected.");
        }
        return stb.toString();
    }

    /**
     * Set the proper warning message and activate - or deactivate all.
     */
    private void updateWarnings() {
        final JLabel maxDimZeroComponent = (JLabel)m_maxDimZeroLabel.getComponentPanel().getComponent(0);
        final JLabel tooHighDimComponent = (JLabel)m_tooHighDimLabel.getComponentPanel().getComponent(0);
        if (m_maximumDim <= 0) {
            // AP-10106 case 1
            maxDimZeroComponent.setText(wrapText("Warning: " + createMaxDimZeroWarning()));
            maxDimZeroComponent.setVisible(true);

            tooHighDimComponent.setVisible(false);
        } else if (m_dimensionModel.getIntValue() > m_maximumDim) {
            // AP-10106 case 2
            tooHighDimComponent.setText(wrapText("Warning: " + createTooHighDimWarning()));
            tooHighDimComponent.setVisible(true);

            maxDimZeroComponent.setVisible(false);
        } else {
            // all good
            maxDimZeroComponent.setVisible(false);
            tooHighDimComponent.setVisible(false);
        }
    }

    private void updateSpinner() {
        m_dimensionComponent.setToolTipText("Maximum dimension: " + m_maximumDim);

        final JSpinner spinner = (JSpinner)m_dimensionComponent.getComponentPanel().getComponent(1);
        final JFormattedTextField spinnerTextField = ((DefaultEditor)spinner.getEditor()).getTextField();
        if (m_maximumDim <= 0) {
            spinnerTextField.setBackground(Color.RED);
            // en-/disabeling the spinner fires its change listener, causing the boundaries to adjust...
            // circumvent the firing of the change listener by disabling the components individually
            spinnerTextField.setEnabled(false);
            spinner.setEnabled(false);
        } else if (m_dimensionModel.getIntValue() > m_maximumDim) {
            spinnerTextField.setBackground(Color.RED);
            spinnerTextField.setEnabled(true);
            spinner.setEnabled(true);
        } else {
            spinnerTextField.setBackground(Color.WHITE);
            spinnerTextField.setEnabled(true);
            spinner.setEnabled(true);
        }
    }

    private void updateSettings() {
        // Save number of columns and number of classes to show a specific warning.
        if ((m_lastSpecs != null) && (m_lastSpecs.length > 0) && (m_classColComponent.getSelected() != null)
                && (m_classColComponent.getSelected().length() > 0)) {
            m_selectedColumns = m_usedColsModel.applyTo(m_lastSpecs[0]).getIncludes().length;
            final DataColumnDomain domain = m_lastSpecs[0].getColumnSpec(m_classColComponent.getSelected()).getDomain();
            if (domain.hasValues()) {
                m_selectedClasses = domain.getValues().size();
            } else {
                // set to a flag value indicating that the domain was not calculated
                m_selectedClasses = -1;
            }
        }

        m_maximumDim = Math.min(m_selectedClasses - 1, m_selectedColumns);

        updateSpinner();
        updateWarnings();
    }

    private void validateSettings() throws InvalidSettingsException {
        if (m_maximumDim <= 0) {
            throw new InvalidSettingsException(createMaxDimZeroWarning());
        }
        if (m_dimensionModel.getIntValue() > m_maximumDim) {
            throw new InvalidSettingsException(createTooHighDimWarning());
        }
    }
}
