/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.forms;

import java.awt.BorderLayout;
import java.awt.Color;
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
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import uniarchive.graphics.IconManager;
import uniarchive.models.archive.ArchiveDb;
import uniarchive.models.archive.IMArchive;
import uniarchive.widgets.GridBagPanel;
import uniarchive.widgets.HorizEtchedLine;
import uniarchive.widgets.UIUtils;
import uniarchive.widgets.ViewLabel;

/**
 * Class for a dialog that allows the user to select
 * a destination for a newly imported archive.
 */
public class ImportFinishedDialog extends JDialog
{
	private static final long serialVersionUID = 1;
	
	public enum Action { CREATE_NEW, REPLACE, MERGE_INTO, CANCELLED };
	
	protected static final String COMMAND_ACCEPT = "accept";
	protected static final String COMMAND_CANCEL = "cancel";
	
	protected static final Integer TOKEN_NEW_ARCHIVE = new Integer(1);
	protected static final Integer TOKEN_CURRENT_ARCHIVE = new Integer(2);
	
	protected JButton _okButton;
	protected JList _archivesListBox;
	protected JTextField _nameField;
	protected JRadioButton _radioCopy;
	protected JRadioButton _radioMerge;
	protected JRadioButton _radioAccounting;
	protected JRadioButton _radioAllData;
	protected JCheckBox _checkConnect;
	
	protected List<String> _archivesList = null;
	protected String _currArchive = null;
	
	public Action resultAction;
	public String resultArchiveName;
	public boolean resultAccountingOnly;
	public boolean resultConnectAfter;
	
	protected CommandButtonListener _cmdButtonListener = new CommandButtonListener();
	
	/**
	 * Constructor.
	 * 
	 * @param owner The parent window for this dialog
	 */
	public ImportFinishedDialog(Window owner)
	{
		super(owner, "Archive Data Import", Dialog.DEFAULT_MODALITY_TYPE);
		
		this._initUI();
	}
	
	/**
	 * Shows the dialog.
	 * 
	 * @param archive The recently imported archive
	 * @param currArchive The name of the currently connected archive,
	 *                    or null if none is connected
	 * @return The action desired by the user, as an Action constant.
	 */
	public Action showDialog(IMArchive archive, String currArchiveName)
	{
		try { this._archivesList = ArchiveDb.getInstance().getArchives(); } catch (Exception e) { };
		this._currArchive = currArchiveName;
		
		this._updateArchivesListBox();
		this._archivesListBox.setSelectedValue(TOKEN_NEW_ARCHIVE, true);
		this._radioAllData.setSelected(true);
		this._checkConnect.setSelected(true);
		this._nameField.requestFocusInWindow();
	
		this.setVisible(true); // Function blocks here
		
		return this.resultAction;
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
		
		this.add(this._createTopPanel(), BorderLayout.NORTH);
		this.add(this._createMainPanel(), BorderLayout.CENTER);
		this.add(this._createBottomPanel(), BorderLayout.SOUTH);
		
		this.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		Dimension screenSize = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
		Dimension formSize = new Dimension(screenSize.width*3/7, screenSize.height*3/7);
		
		this.setSize(formSize);
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
		this._archivesListBox.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		this._archivesListBox.setCellRenderer(new ArchivesListRenderer());
		int fontSize = this._archivesListBox.getFont().getSize();
		this._archivesListBox.setMinimumSize(new Dimension(fontSize*16,fontSize*8));
		this._archivesListBox.setPreferredSize(new Dimension(fontSize*16,fontSize*8));
		
		ButtonGroup radios1 = new ButtonGroup();
		this._radioCopy = new JRadioButton("Replace the data in the archive with the imported data");
		this._radioMerge = new JRadioButton("Merge the imported data into the archive");
		radios1.add(this._radioCopy);
		radios1.add(this._radioMerge);
		
		ButtonGroup radios2 = new ButtonGroup();
		this._radioAllData = new JRadioButton("Accounting and conversations");
		this._radioAccounting = new JRadioButton("Accounting only");
		radios2.add(this._radioAllData);
		radios2.add(this._radioAccounting);
		
		this._checkConnect = new JCheckBox("Connect to this archive after the import");
		
		GridBagPanel mainPanel = new GridBagPanel();
		mainPanel.add(new ViewLabel("Destination"),            0, 0, 1, 1, 0.0, 0.0, mainPanel.WEST,   mainPanel.NONE,  0, 0,16, 0);
		mainPanel.add(new JScrollPane(this._archivesListBox),  0, 1, 1, 8, 0.0, 1.0, mainPanel.WEST,   mainPanel.VERT,  0, 0,16, 0);
		mainPanel.add(new JLabel("Archive name"),              1, 1, 1, 1, 0.0, 0.0, mainPanel.WEST,   mainPanel.NONE,  0, 0, 8, 8);
		mainPanel.add(this._nameField,                         2, 1, 1, 1, 1.0, 0.0, mainPanel.CENTER, mainPanel.HORIZ, 0, 0, 0,10);
		mainPanel.add(new ViewLabel("Action"),                 1, 2, 2, 1, 1.0, 0.0, mainPanel.WEST,   mainPanel.HORIZ, 0, 0, 0, 4);
		mainPanel.add(this._radioCopy,                         1, 3, 2, 1, 1.0, 0.0, mainPanel.WEST,   mainPanel.HORIZ, 0, 0, 0, 0);
		mainPanel.add(this._radioMerge,                        1, 4, 2, 1, 1.0, 0.0, mainPanel.WEST,   mainPanel.HORIZ, 0, 0, 0,10);
		mainPanel.add(new ViewLabel("Import"),                 1, 5, 2, 1, 1.0, 0.0, mainPanel.WEST,   mainPanel.HORIZ, 0, 0, 0, 4);
		mainPanel.add(this._radioAllData,                      1, 6, 2, 1, 1.0, 0.0, mainPanel.WEST,   mainPanel.HORIZ, 0, 0, 0, 0);
		mainPanel.add(this._radioAccounting,                   1, 7, 2, 1, 1.0, 0.0, mainPanel.WEST,   mainPanel.HORIZ, 0, 0, 0, 8);
		mainPanel.add(this._checkConnect,                      1, 8, 2, 1, 1.0, 1.0, mainPanel.SW,     mainPanel.HORIZ, 0, 0, 0, 0);
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
		
		this._archivesListBox.addListSelectionListener(new ListSelectionListener()
			{
				@Override
				public void valueChanged(ListSelectionEvent e) { _onDestinationSelected(); }
			});
		
		return mainPanel;
	}
	
	/**
	 * Initializes the top panel and all child controls.
	 * 
	 * @return A newly created JPanel
	 */
	protected JPanel _createTopPanel()
	{
		Box panel = new Box(BoxLayout.X_AXIS);
		panel.setOpaque(true);
		panel.setBackground(Color.WHITE);
		panel.add(new JLabel("<html><b>Archive Data Import</b><br><br>"+
				"Please select a destination for the imported data.</html>"));
		panel.add(Box.createHorizontalGlue());
		panel.add(new JLabel(IconManager.getInstance().getIcon("kibble/import_wiz")));
		panel.setBorder(new EmptyBorder(8,8,8,8));
		
		JPanel superPanel = new JPanel(new BorderLayout());
		superPanel.add(panel, BorderLayout.CENTER);
		superPanel.add(new HorizEtchedLine(), BorderLayout.SOUTH);
		
		return superPanel;
	}
	
	/**
	 * Initializes the bottom panel and all child controls.
	 * 
	 * @return A newly created JPanel
	 */
	protected JPanel _createBottomPanel()
	{		
		Box panel = new Box(BoxLayout.X_AXIS);
		
		this._okButton = UIUtils.makeButton("Finish", "accept", this._cmdButtonListener, COMMAND_ACCEPT);
		JButton cancelButton = UIUtils.makeButton("Cancel", "cancel", this._cmdButtonListener, COMMAND_CANCEL);
		UIUtils.copyButtonWidth(this._okButton, cancelButton);
		
		panel.add(Box.createHorizontalGlue());
		panel.add(this._okButton);
		panel.add(Box.createHorizontalStrut(8));
		panel.add(cancelButton);
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
		Object destination = this._archivesListBox.getSelectedValue();
		if (destination == TOKEN_NEW_ARCHIVE)
		{
			this.resultAction = Action.CREATE_NEW;
		}
		else if (this._radioCopy.isSelected())
		{
			this.resultAction = Action.REPLACE;
			
			if (JOptionPane.showConfirmDialog(this,
					"This will replace ALL DATA in the '"+this._nameField.getText()+"' archive.\n\n"+
					"Are you sure you want to continue ?", "Please Confirm",
					JOptionPane.YES_NO_OPTION,
					JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
		}
		else
		{
			this.resultAction = Action.MERGE_INTO;
		}
		
		this.resultArchiveName = this._nameField.getText();
		this.resultConnectAfter = this._checkConnect.isSelected();
		this.resultAccountingOnly = this._radioAccounting.isSelected();
		this.setVisible(false);
	}
	
	/**
	 * Reacts to the pressing of the "cancel" button.
	 */
	protected void _doCancel()
	{
		this.resultAction = Action.CANCELLED;
		this.setVisible(false);
	}
	
	/**
	 * Reacts to a selection made in the Destinations
	 * list box.
	 */
	protected void _onDestinationSelected()
	{
		Object destination = this._archivesListBox.getSelectedValue();
		
		this._nameField.setEnabled(destination == TOKEN_NEW_ARCHIVE);
		this._radioMerge.setEnabled((destination != null) && (destination != TOKEN_NEW_ARCHIVE));
		
		if (destination == TOKEN_NEW_ARCHIVE) this._radioCopy.setSelected(true);
		
		if (destination == TOKEN_CURRENT_ARCHIVE) this._nameField.setText(this._currArchive);
		else if (destination instanceof String) this._nameField.setText((String)destination);
		else this._nameField.setText("");
		
		this._runButtonLogic();
	}
	
	/**
	 * Updates the archives list control to reflect the
	 * current archives list.
	 */
	protected void _updateArchivesListBox()
	{
		DefaultListModel model = (DefaultListModel)this._archivesListBox.getModel();
		
		model.clear();
		model.addElement(TOKEN_NEW_ARCHIVE);
		if (this._currArchive != null) model.addElement(TOKEN_CURRENT_ARCHIVE);
		for (String archive : this._archivesList) if (!archive.equals(this._currArchive)) model.addElement(archive);
	}
	
	/**
	 * Internal class for rendering the Existing Archives list.
	 */
	protected class ArchivesListRenderer extends DefaultListCellRenderer
	{
		private static final long serialVersionUID = 1L;
		
		private ImageIcon _archiveIcon = IconManager.getInstance().getIcon("archive");
		private ImageIcon _newArchiveIcon = IconManager.getInstance().getIcon("archive+ovl_add");
		private ImageIcon _currArchiveIcon = IconManager.getInstance().getIcon("archive_current");
		
		@Override
		public Component getListCellRendererComponent(JList list, Object value,
				int index, boolean isSelected, boolean cellHasFocus)
		{
			JLabel comp = (JLabel)super.getListCellRendererComponent(list, value, index,
					isSelected, cellHasFocus);
			
			if (value == TOKEN_NEW_ARCHIVE)
			{
				comp.setText("New archive");
				comp.setIcon(this._newArchiveIcon);
			}
			else if (value == TOKEN_CURRENT_ARCHIVE)
			{
				comp.setText("Current archive ("+_currArchive+")");
				comp.setIcon(this._currArchiveIcon);
			}
			else
			{
				comp.setIcon(this._archiveIcon);
			}
			
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
