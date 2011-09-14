/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.forms;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import uniarchive.models.archive.IMArchive.Account;
import uniarchive.models.archive.IMArchive.Contact;
import uniarchive.models.archive.IMArchive.Group;
import uniarchive.widgets.ArchiveItemDisplay;
import uniarchive.widgets.ArchiveItemsCombo;
import uniarchive.widgets.GridBagPanel;
import uniarchive.widgets.HorizEtchedLine;
import uniarchive.widgets.UIUtils;

/**
 * Class for a generic dialog that allows specifying
 * parameters for operations in an archive.
 */
public class ArchiveOperationsDialog extends JDialog
{
	private static final long serialVersionUID = 1;
	
	protected static final String COMMAND_ACCEPT = "accept";
	protected static final String COMMAND_CANCEL = "cancel";
	
	protected static enum Mode { ADD_CONTACT, ADD_GROUP, ADD_IDENTITY, MERGE_ITEMS, MOVE_ITEMS };
	
	public boolean cancelled;
	public String resultItemName;
	public Object resultContainer;
	
	protected Mode _dialogMode = null;
	
	protected JButton _okButton;
	protected JLabel _nameLabel;
	protected JTextField _nameField;
	protected JLabel _containerLabel;
	protected ArchiveItemsCombo _containerCombo;
	protected JLabel _itemLabel;
	protected ArchiveItemDisplay _itemDisplay;
	
	protected CommandButtonListener _cmdButtonListener = new CommandButtonListener();
	
	/**
	 * Constructor.
	 * 
	 * @param owner The parent window for this dialog
	 */
	public ArchiveOperationsDialog(Window owner)
	{
		super(owner, "Add Item", Dialog.DEFAULT_MODALITY_TYPE);
		
		this._initUI();
	}
	
	/**
	 * Shows a dialog appropriate for creating a
	 * new group.
	 * 
	 * @return True if the user pressed OK, false if
	 *         s/he cancelled
	 */
	public boolean showAddGroup()
	{
		this.setTitle("Add Group");
		this._dialogMode = Mode.ADD_GROUP;
		
		this._nameLabel.setVisible(true);
		this._nameLabel.setText("Name");
		this._nameField.setVisible(true);
		this._itemLabel.setVisible(false);
		this._itemDisplay.setVisible(false);
		this._containerLabel.setVisible(false);
		this._containerCombo.setVisible(false);
		this.pack();
		this.setLocationRelativeTo(null);
		
		this._nameField.setText("");
		this.cancelled = false;
		this._runButtonLogic();
		
		this.setVisible(true); // Function blocks here
		
		return !this.cancelled;
	}
	
	/**
	 * Shows a dialog appropriate for creating a
	 * new identity.
	 * 
	 * @return True if the user pressed OK, false if
	 *         s/he cancelled
	 */
	public boolean showAddIdentity()
	{
		this.setTitle("Add Identity");
		this._dialogMode = Mode.ADD_IDENTITY;
		
		this._nameLabel.setVisible(true);
		this._nameLabel.setText("Name");
		this._nameField.setVisible(true);
		this._itemLabel.setVisible(false);
		this._itemDisplay.setVisible(false);
		this._containerLabel.setVisible(false);
		this._containerCombo.setVisible(false);
		this.pack();
		this.setLocationRelativeTo(null);
		
		this._nameField.setText("");
		this.cancelled = false;
		this._runButtonLogic();
		
		this.setVisible(true); // Function blocks here
		
		return !this.cancelled;
	}
	
	/**
	 * Shows a dialog appropriate for creating a
	 * new contact.
	 * 
	 * @param groups A list of groups in which the contact
	 *               can be placed
	 * @param defaultGroup The group selected by default
	 * @return True if the user pressed OK, false if
	 *         s/he cancelled
	 */
	public boolean showAddContact(List<Group> groups, Group defaultGroup)
	{
		this.setTitle("Add Contact");
		this._dialogMode = Mode.ADD_CONTACT;
		
		this._nameLabel.setVisible(true);
		this._nameLabel.setText("Name");
		this._nameField.setVisible(true);
		this._itemLabel.setVisible(false);
		this._itemDisplay.setVisible(false);
		this._containerLabel.setVisible(true);
		this._containerLabel.setText("Add in");
		this._containerCombo.setVisible(true);
		this.pack();
		this.setLocationRelativeTo(null);
		
		this._containerCombo.setItems(groups);
		this._containerCombo.setSelectedItem(defaultGroup);
		this._nameField.setText("");
		this.cancelled = false;
		this._runButtonLogic();
		
		this.setVisible(true); // Function blocks here
		
		return !this.cancelled;
	}
	
	/**
	 * Shows a dialog appropriate for a merge operation.
	 * 
	 * @param items A list of items to be merged into another
	 * @param into A list of candidates for the item that is
	 *             all of the items will be merged into
	 * @param defaultInto The merge-into item selected by default
	 * @return True if the user pressed OK, false if
	 *         s/he cancelled
	 */
	public boolean showMergeItems(List<Object> items, List<Object> into, Object defaultInto)
	{
		if (items.isEmpty()) return false;
		if (items.get(0) instanceof Group) { this.setTitle("Merge Groups"); }
		else if (items.get(0) instanceof Contact) { this.setTitle("Merge Contacts"); }
		else { this.setTitle("Merge Items"); }
		this._dialogMode = Mode.MERGE_ITEMS;
		
		this._nameLabel.setVisible(false);
		this._nameField.setVisible(false);
		this._itemLabel.setVisible(true);
		this._itemLabel.setText("Merge");
		this._itemDisplay.setVisible(true);
		this._containerLabel.setVisible(true);
		this._containerLabel.setText("Into");
		this._containerCombo.setVisible(true);
		this.pack();
		this.setLocationRelativeTo(null);
		
		this._itemDisplay.displayItems(items);
		this._containerCombo.setItems(into);
		this._containerCombo.setSelectedItem(defaultInto);
		this.cancelled = false;
		this._runButtonLogic();
		
		this.setVisible(true); // Function blocks here
		
		return !this.cancelled;
	}
	
	/**
	 * Shows a dialog appropriate for a move operation.
	 * 
	 * @param items A list of items to be moved
	 * @param locations A list of destinations
	 * @param defaultLocation The destination selected by default
	 * @return True if the user pressed OK, false if s/he cancelled
	 */
	public boolean showMoveItems(List<Object> items, List<Object> locations, Object defaultLocation)
	{
		if (items.isEmpty()) return false;
		if (items.get(0) instanceof Group) { this.setTitle("Move Group(s)"); }
		else if (items.get(0) instanceof Contact) { this.setTitle("Move Contact(s)"); }
		else if (items.get(0) instanceof Account) { this.setTitle("Move Account(s)"); }
		else { this.setTitle("Move Items"); }
		this._dialogMode = Mode.MOVE_ITEMS;
		
		this._nameLabel.setVisible(false);
		this._nameField.setVisible(false);
		this._itemLabel.setVisible(true);
		this._itemLabel.setText("Move");
		this._itemDisplay.setVisible(true);
		this._containerLabel.setVisible(true);
		this._containerLabel.setText((items.get(0) instanceof Group) ? "Before" : "Under");
		this._containerCombo.setVisible(true);
		this.pack();
		this.setLocationRelativeTo(null);
		
		this._itemDisplay.displayItems(items);
		this._containerCombo.setItems(locations);
		this._containerCombo.setSelectedItem(defaultLocation);
		this.cancelled = false;
		this._runButtonLogic();
		
		this.setVisible(true); // Function blocks here
		
		return !this.cancelled;
	}
	
	/**
	 * Enables or disables internal controls
	 * according to the state of the control.
	 */
	protected void _runButtonLogic()
	{
		switch (this._dialogMode)
		{
		case ADD_CONTACT:
		case ADD_GROUP:
		case ADD_IDENTITY:
			this._okButton.setEnabled(!this._nameField.getText().isEmpty());
			break;
		case MERGE_ITEMS:
		case MOVE_ITEMS:
			this._okButton.setEnabled(this._containerCombo.getSelectedItem() != null);
			break;
		}
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
		this._itemDisplay = new ArchiveItemDisplay();
		this._containerCombo = new ArchiveItemsCombo();
		
		this._nameLabel = new JLabel("Name");
		this._itemLabel = new JLabel("Item");
		this._containerLabel = new JLabel("Add in");
		
		GridBagPanel mainPanel = new GridBagPanel();
		mainPanel.add(this._nameLabel,      0, 0, 1, 1, 0.0, 0.0, mainPanel.WEST,   mainPanel.NONE,  0, 0, 8, 0);
		mainPanel.add(this._nameField,      1, 0, 1, 1, 1.0, 0.0, mainPanel.CENTER, mainPanel.HORIZ, 0, 0, 0, 0);
		mainPanel.add(this._itemLabel,      0, 1, 1, 1, 0.0, 0.0, mainPanel.WEST,   mainPanel.NONE,  0, 0, 8, 0);
		mainPanel.add(this._itemDisplay,    1, 1, 1, 1, 1.0, 0.0, mainPanel.CENTER, mainPanel.HORIZ, 0, 0, 0, 0);
		mainPanel.add(this._containerLabel, 0, 2, 1, 1, 0.0, 0.0, mainPanel.WEST,   mainPanel.NONE,  0, 8, 8, 0);
		mainPanel.add(this._containerCombo, 1, 2, 1, 1, 1.0, 0.0, mainPanel.CENTER, mainPanel.HORIZ, 0, 8, 0, 0);
		mainPanel.setBorder(new EmptyBorder(8,8,8,8));
		
		this._containerCombo.addItemListener(new ItemListener()
			{
				@Override
				public void itemStateChanged(ItemEvent evt) { _runButtonLogic(); }
			});
		
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
		this.cancelled = false;
		this.resultItemName = this._nameField.getText();
		this.resultContainer = this._containerCombo.getSelectedItem();
		this.setVisible(false);
	}
	
	/**
	 * Reacts to the pressing of the "cancel" button.
	 */
	protected void _doCancel()
	{
		this.cancelled = true;
		this.resultItemName = null;
		this.resultContainer = null;
		this.setVisible(false);
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
