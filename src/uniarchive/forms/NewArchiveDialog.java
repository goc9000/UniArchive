/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.forms;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import uniarchive.graphics.IconManager;
import uniarchive.models.archive.ArchiveDb;
import uniarchive.widgets.GridBagPanel;
import uniarchive.widgets.HorizEtchedLine;
import uniarchive.widgets.UIUtils;
import uniarchive.widgets.ViewLabel;

/**
 * Class for a dialog that allows the user to choose the name
 * of a new archive while also being presented with a list of
 * the existing archives in the database.
 */
public class NewArchiveDialog extends JDialog
{
	private static final long serialVersionUID = 1;
	
	protected static final String COMMAND_ACCEPT = "accept";
	protected static final String COMMAND_CANCEL = "cancel";
	
	protected JButton _okButton;
	protected JTextField _nameField;
	protected JList _archivesListBox;
	
	protected List<String> _archivesList = null;
	
	protected boolean _cancelled = false;
	
	protected CommandButtonListener _cmdButtonListener = new CommandButtonListener();
	
	/**
	 * Constructor.
	 * 
	 * @param owner The parent window for this dialog
	 */
	public NewArchiveDialog(Window owner)
	{
		super(owner, "Create Archive", Dialog.DEFAULT_MODALITY_TYPE);
		
		this._initUI();
	}
	
	/**
	 * Shows the dialog.
	 * 
	 * @return The name chosen for the new archive, or null if
	 *         the user cancelled.
	 */
	public String showDialog()
	{
		this._cancelled = false;
	
		try { this._archivesList = ArchiveDb.getInstance().getArchives(); } catch (Exception e) { };
		
		this._updateArchivesListBox();
		this._nameField.setText("");
		this._nameField.requestFocusInWindow();
		this._runButtonLogic();
	
		this.setVisible(true); // Function blocks here
		
		return (!this._cancelled) ? this._nameField.getText() : null;
	}
	
	/**
	 * Enables or disables internal controls
	 * according to the state of the control.
	 */
	protected void _runButtonLogic()
	{
		this._okButton.setEnabled(!this._nameField.getText().isEmpty());
	}

	/**
	 * Initializes the form GUI.
	 */
	protected void _initUI()
	{
		this.setLayout(new BorderLayout());
		
		this.add(this._createMainPanel(), BorderLayout.CENTER);
		this.add(this._createBottomPanel(), BorderLayout.SOUTH);
		
		this.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		
		this.pack();
		this.setLocationRelativeTo(this.getOwner());
		this.setResizable(false);
	}
	
	/**
	 * Initializes the main panel and all child controls.
	 * 
	 * @return A newly created JPanel
	 */
	protected JPanel _createMainPanel()
	{
		this._nameField = new JTextField();
		
		this._archivesListBox = new JList(new DefaultListModel());
		this._archivesListBox.setBackground(new JPanel().getBackground());
		this._archivesListBox.setCellRenderer(new ArchivesListRenderer());
		this._archivesListBox.setFocusable(false);
		
		GridBagPanel mainPanel = new GridBagPanel();
		mainPanel.add(new JLabel("Name"),                      0, 0, 1, 1, 0.0, 0.0, mainPanel.WEST,   mainPanel.NONE,  0, 0, 8, 0);
		mainPanel.add(this._nameField,                         1, 0, 1, 1, 1.0, 0.0, mainPanel.CENTER, mainPanel.HORIZ, 0, 0, 0, 0);
		mainPanel.add(new ViewLabel("Existing archives"),      0, 1, 2, 1, 1.0, 0.0, mainPanel.WEST,   mainPanel.NONE,  0, 8, 0, 0);
		mainPanel.add(new JScrollPane(this._archivesListBox),  0, 2, 2, 1, 1.0, 1.0, mainPanel.CENTER, mainPanel.BOTH,  0, 8, 0, 0);
		mainPanel.setBorder(new EmptyBorder(8,8,8,8));
		
		this._nameField.getDocument().addDocumentListener(new DocumentListener()
				{
					@Override
					public void changedUpdate(DocumentEvent evt) { _runButtonLogic(); }
					@Override
					public void insertUpdate(DocumentEvent evt) { _runButtonLogic(); }
					@Override
					public void removeUpdate(DocumentEvent evt) {_runButtonLogic(); }
				});
		
		// Typing Enter is equivalent to pressing the OK button
		this._nameField.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent evt)
				{
					if (_okButton.isEnabled()) _doAccept();
				}		
			});
		
		return mainPanel;
	}
	
	/**
	 * Initializes the bottom panel and all child controls.
	 * 
	 * @return A newly created JPanel
	 */
	protected JPanel _createBottomPanel()
	{		
		Box panel = new Box(BoxLayout.X_AXIS);
		
		this._okButton = UIUtils.makeButton("OK", "accept", this._cmdButtonListener, COMMAND_ACCEPT);
		JButton cancelButton = UIUtils.makeButton("Cancel", "cancel", this._cmdButtonListener, COMMAND_CANCEL);
		UIUtils.copyButtonWidth(this._okButton, cancelButton);
		
		panel.add(Box.createHorizontalGlue());
		panel.add(this._okButton);
		panel.add(Box.createHorizontalStrut(8));
		panel.add(cancelButton);
		panel.add(Box.createHorizontalGlue());
		panel.setBorder(new EmptyBorder(8,8,8,8));
		
		Dimension dim = new Dimension(this._okButton.getFont().getSize()*22, panel.getPreferredSize().height);
		panel.setMinimumSize(dim);
		panel.setPreferredSize(dim);
		
		JPanel superPanel = new JPanel(new BorderLayout());
		superPanel.add(new HorizEtchedLine(), BorderLayout.NORTH);
		superPanel.add(panel, BorderLayout.CENTER);
		
		return superPanel;
	}
	
	/**
	 * Reacts to the pressing of the "accept" button.
	 */
	protected void _doAccept()
	{
		// Check whether an archive with the name entered
		// by the user already exists
		if (this._archivesList.contains(this._nameField.getText()))
		{
			JOptionPane.showMessageDialog(this, "An archive by that name already exists.",
					"Error", JOptionPane.WARNING_MESSAGE);
			return;
		}
		
		this._cancelled = false;
		this.setVisible(false);
	}
	
	/**
	 * Reacts to the pressing of the "cancel" button.
	 */
	protected void _doCancel()
	{
		this._cancelled = true;
		this.setVisible(false);
	}
	
	/**
	 * Updates the archives list control to reflect the
	 * current archives list.
	 */
	protected void _updateArchivesListBox()
	{
		DefaultListModel model = (DefaultListModel)this._archivesListBox.getModel();
		
		model.clear();
		for (String archive : this._archivesList) model.addElement(archive);
	}
	
	/**
	 * Internal class for rendering the Existing Archives list.
	 */
	protected static class ArchivesListRenderer extends DefaultListCellRenderer
	{
		private static final long serialVersionUID = 1L;
		
		private ImageIcon _archiveIcon = IconManager.getInstance().getIcon("archive");

		@Override
		public Component getListCellRendererComponent(JList list, Object value,
				int index, boolean isSelected, boolean cellHasFocus)
		{
			JLabel comp = (JLabel)super.getListCellRendererComponent(list, value, index,
					false, false);
			
			comp.setIcon(this._archiveIcon);
			
			return comp;
		}
	}
		
	/**
	 * Internal class that listens to click events on command
	 * buttons contained in this form and starts the corresponding
	 * actions.
	 */
	protected class CommandButtonListener implements ActionListener
	{
		@Override
		public void actionPerformed(ActionEvent evt)
		{
			if (evt.getSource() instanceof AbstractButton)
			{
				String commandId = evt.getActionCommand();
				
				if (commandId.equals(ArchiveOperationsDialog.COMMAND_ACCEPT))
				{
					_doAccept();
				}
				else if (commandId.equals(ArchiveOperationsDialog.COMMAND_CANCEL))
				{
					_doCancel();
				}
			}
		}
	}
}
