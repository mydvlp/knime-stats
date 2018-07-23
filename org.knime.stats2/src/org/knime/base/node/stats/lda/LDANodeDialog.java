package org.knime.base.node.stats.lda;

import java.util.Set;

import javax.swing.BoxLayout;
import javax.swing.JPanel;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataTableSpec;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;
import org.knime.core.node.defaultnodesettings.DialogComponentColumnFilter2;
import org.knime.core.node.defaultnodesettings.DialogComponentColumnNameSelection;
import org.knime.core.node.defaultnodesettings.DialogComponentNumber;
import org.knime.core.node.defaultnodesettings.SettingsModelIntegerBounded;

/**
 * <code>NodeDialog</code> for the "PMMLToJavascriptCompiler" Node.
 *
 *
 * This node dialog derives from {@link DefaultNodeSettingsPane} which allows
 * creation of a simple dialog with standard components. If you need a more
 * complex dialog please derive directly from
 * {@link org.knime.core.node.NodeDialogPane}.
 *
 * @author Alexander Fillbrunn
 */
public class LDANodeDialog extends NodeDialogPane {

    private JPanel m_panel;
    private DialogComponentNumber m_k;
    private DialogComponentColumnNameSelection m_classCol;
    private DialogComponentColumnFilter2 m_usedCols;

    private DataTableSpec[] m_specs;
    /**
     * New pane for configuring the node.
     */
    @SuppressWarnings("unchecked")
    protected LDANodeDialog() {
        m_classCol = new DialogComponentColumnNameSelection(LDANodeModel.createClassColSettingsModel(),
                      "Class column", 0, org.knime.core.data.NominalValue.class);
        m_usedCols = new DialogComponentColumnFilter2(
                                               LDANodeModel.createUsedColsSettingsModel(), 0);

        m_k = new DialogComponentNumber(new SettingsModelIntegerBounded(LDANodeModel.K_CFG,
            1, 1, Integer.MAX_VALUE), "Dimensions", 1);
        m_classCol.getModel().addChangeListener(cl -> updateSpinnerModel());

        m_panel = new JPanel();
        BoxLayout bl = new BoxLayout(m_panel, 1);
        m_panel.setLayout(bl);
        m_panel.add(m_classCol.getComponentPanel());
        m_panel.add(m_usedCols.getComponentPanel());
        addTab("Settings", m_panel);
    }

    private void updateSpinnerModel() {
        if (m_specs == null) {
            return;
        }
        int val = ((SettingsModelIntegerBounded)m_k.getModel()).getIntValue();
        int max = Integer.MAX_VALUE;
        if (m_classCol.getSelected() != null && m_classCol.getSelected().length() > 0) {
            Set<DataCell> vals = m_specs[0].getColumnSpec(m_classCol.getSelected()).getDomain().getValues();
            if (vals != null && !vals.isEmpty()) {
                max = vals.size() - 1;
                val = Math.min(val, max);
            }
        }
        m_k = new DialogComponentNumber(new SettingsModelIntegerBounded(LDANodeModel.K_CFG,
            val, 1, max), "Dimensions", 1);
        if (m_panel.getComponents().length > 2) {
            m_panel.remove(0);
        }
        m_panel.add(m_k.getComponentPanel(), 0);
        m_panel.revalidate();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final DataTableSpec[] specs)
            throws NotConfigurableException {
        m_k.loadSettingsFrom(settings, specs);
        m_usedCols.loadSettingsFrom(settings, specs);
        m_specs = specs;

        // Needs to be loaded last as this will trigger the listener updating the spinner bounds
        // which expects the value for k to be already loaded.
        m_classCol.loadSettingsFrom(settings, specs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        m_classCol.saveSettingsTo(settings);
        m_usedCols.saveSettingsTo(settings);
        m_k.saveSettingsTo(settings);
    }
}

