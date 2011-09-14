/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.forms;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import uniarchive.forms.importing.DigsbyImportDialog;
import uniarchive.forms.importing.GaimImportDialog;
import uniarchive.forms.importing.ImportDialog;
import uniarchive.forms.importing.MsnImportDialog;
import uniarchive.forms.importing.YahooImportDialog;
import uniarchive.graphics.IconManager;
import uniarchive.models.ProgressEvent;
import uniarchive.models.ProgressListener;
import uniarchive.models.archive.ArchiveDb;
import uniarchive.models.archive.ConversationsQuery;
import uniarchive.models.archive.IMArchive;
import uniarchive.models.archive.IMArchiveEvent;
import uniarchive.models.archive.IMArchiveJsonReader;
import uniarchive.models.archive.IMArchiveJsonWriter;
import uniarchive.models.archive.IMArchiveListener;
import uniarchive.models.archive.IMArchive.Account;
import uniarchive.models.archive.IMArchive.Contact;
import uniarchive.models.archive.IMArchive.Group;
import uniarchive.widgets.ArchiveGroupsView;
import uniarchive.widgets.ChatView;
import uniarchive.widgets.ConversationsView;
import uniarchive.widgets.GridBagPanel;
import uniarchive.widgets.UIUtils;
import uniarchive.widgets.ViewLabel;

/**
 * Class for the main form.
 */
public class MainForm extends JFrame
{
	private static final long serialVersionUID = 1;
	
	protected static final String COMMAND_NOP = "nop";
	
	protected static final String COMMAND_NEW_ARCHIVE = "newArchive";
	protected static final String COMMAND_CONNECT = "connect";
	protected static final String COMMAND_DISCONNECT = "disconnect";
	protected static final String COMMAND_END_PROGRAM = "endProgram";
	
	protected static final String COMMAND_EXPORT_TO_FILE = "exportToFile";
	
	protected static final String COMMAND_IMPORT_FROM_FILE = "importFromFile";
	protected static final String COMMAND_IMPORT_FROM_ARCHIVE = "importFromArchive";
	protected static final String COMMAND_IMPORT_FROM_GAIM = "importFromGaim";
	protected static final String COMMAND_IMPORT_FROM_YAHOO = "importFromYahoo";
	protected static final String COMMAND_IMPORT_FROM_DIGSBY = "importFromDigsby";
	protected static final String COMMAND_IMPORT_FROM_MSN = "importFromMsn";
	
	protected static final String COMMAND_CREATE_GROUP = "createGroup";
	protected static final String COMMAND_CREATE_CONTACT = "createContact";
	protected static final String COMMAND_CREATE_IDENTITY = "createIdentity";
	
	protected static final String COMMAND_DELETE_SELECTED = "deleteSelected";
	protected static final String COMMAND_RENAME_SELECTED = "renameSelected";
	protected static final String COMMAND_MOVE_SELECTED = "moveSelected";
	protected static final String COMMAND_MERGE_SELECTED = "mergeSelected";

	protected static final String COMMAND_SEARCH = "search";
	protected static final String COMMAND_FIND_FIRST = "findFirst";
	protected static final String COMMAND_FIND_NEXT = "findNext";
	protected static final String COMMAND_FIND_PREV = "findPrev";
	protected static final String COMMAND_CLEAR_FIND = "clearFind";
	
	protected IMArchive _archive;
	protected List<String> _archives;
	
	protected ArchiveGroupsView _groupsView;
	protected ConversationsView _conversationsView;
	protected ChatView _chatView;
	
	protected JFileChooser _fileChooser;
	protected GaimImportDialog _gaimImportForm;
	protected YahooImportDialog _yahooImportForm;
	protected DigsbyImportDialog _digsbyImportForm;
	protected MsnImportDialog _msnImportForm;
	protected NewArchiveDialog _newArchiveDialog;
	protected ImportFinishedDialog _importFinishedDialog;
	protected ProgressDialog _progressDialog;
	
	protected JMenu _menuOperations;
	protected JMenu _menuSearch;
	protected JMenu _menuConnect;
	protected JMenu _menuExport;
	protected JMenu _menuImportFromArchive;
	protected JMenuItem _menuItemDisconnect;
	protected JMenuItem _menuItemAddGroup;
	protected JMenuItem _menuItemAddContact;
	protected JMenuItem _menuItemAddIdentity;
	protected JMenuItem _menuItemDeleteSelected;
	protected JMenuItem _menuItemRenameSelected;
	protected JMenuItem _menuItemMoveSelected;
	protected JMenuItem _menuItemMergeSelected;
	protected JMenuItem _menuItemFind;
	protected JMenuItem _menuItemFindNext;
	protected JMenuItem _menuItemFindPrev;
	protected JMenuItem _menuItemClearFind;
	
	protected CommandButtonListener _cmdButtonListener = new CommandButtonListener();
	protected IMArchiveListener _archiveListener = new ArchiveListener();
	
	/**
	 * Constructor.
	 */
	public MainForm()
	{
		this._initUI();
		
		this._archive = null;
		this._archives = new ArrayList<String>();
		
		this._updateArchivesList();
		this._setArchive(null);
	}
	
	/**
	 * Initializes the form GUI.
	 */
	protected void _initUI()
	{
		this.setLayout(new BorderLayout());
		
		this.add(this._createMainPanel(), BorderLayout.CENTER);
		
		this.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		Dimension screenSize = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
		Dimension formSize = new Dimension(screenSize.width*5/6, screenSize.height*3/4);
		
		this.setTitle("Universal IM Archive");
		this.setJMenuBar(this._createMainMenu());
		this.setSize(formSize);
		this.setLocationRelativeTo(null);
		ImageIcon appIcon = IconManager.getInstance().getIcon("appicon");
		if (appIcon != null) this.setIconImage(appIcon.getImage());
		
		this.addWindowListener(
			new WindowAdapter()
			{
				public void windowClosing(WindowEvent w)
				{
					_onClose();
				}
			}
		);
		
		this._createChildDialogs();
	}
	
	/**
	 * Create all child dialogs used by this form.
	 */
	protected void _createChildDialogs()
	{
		// File chooser
		this._fileChooser = new JFileChooser();
		this._fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		this._fileChooser.setMultiSelectionEnabled(false);
		this._fileChooser.addChoosableFileFilter(new FileNameExtensionFilter("IM Archive in JSON Format (*.json)", "json"));
		
		// Import forms
		this._gaimImportForm = new GaimImportDialog(this);
		this._yahooImportForm = new YahooImportDialog(this);
		this._digsbyImportForm = new DigsbyImportDialog(this);
		this._msnImportForm = new MsnImportDialog(this);
		
		// Other dialogs
		this._progressDialog = new ProgressDialog(this);
		this._newArchiveDialog = new NewArchiveDialog(this);
		this._importFinishedDialog = new ImportFinishedDialog(this);
	}
	
	/**
	 * Creates the main menu.
	 * 
	 * @return The created JMenuBar
	 */
	protected JMenuBar _createMainMenu()
	{	
		this._menuOperations = this._createOperationsMenu();
		this._menuSearch = this._createSearchMenu();
		
		JMenuBar menuBar = new JMenuBar();
		menuBar.add(this._createFileMenu());
		menuBar.add(this._menuOperations);
		menuBar.add(this._menuSearch);
		
		return menuBar;
	}
	
	/**
	 * Creates the File menu.
	 * 
	 * @return The created JMenu
	 */
	protected JMenu _createFileMenu()
	{	
		this._menuConnect = UIUtils.makeMenu("Connect To", "connect");
		this._menuItemDisconnect = UIUtils.makeMenuItem("Disconnect", "disconnect", this._cmdButtonListener, COMMAND_DISCONNECT);
		this._menuExport = this._createExportSubmenu();
		
		JMenu fileMenu = new JMenu("File");
		fileMenu.add(UIUtils.makeMenuItem("New Archive...", "archive+ovl_add", this._cmdButtonListener, COMMAND_NEW_ARCHIVE));
		fileMenu.add(this._menuConnect);
		fileMenu.add(this._menuItemDisconnect);
		fileMenu.add(this._createImportSubmenu());
		fileMenu.add(this._menuExport);
		fileMenu.add(new JSeparator());
		fileMenu.add(UIUtils.makeMenuItem("Exit Program", "exit", this._cmdButtonListener, COMMAND_END_PROGRAM));
		
		return fileMenu;
	}
	
	/**
	 * Creates the Import submenu.
	 * 
	 * @return The created JMenu
	 */
	protected JMenu _createImportSubmenu()
	{	
		this._menuImportFromArchive = UIUtils.makeMenu("From Archive", "archive+ovl_out");
		
		JMenu importMenu = UIUtils.makeMenu("Import", "archive+ovl_in");
		importMenu.add(UIUtils.makeMenuItem("From File...", "drive", this._cmdButtonListener, COMMAND_IMPORT_FROM_FILE));
		importMenu.add(this._menuImportFromArchive);
		importMenu.add(new JSeparator());
		importMenu.add(UIUtils.makeMenuItem("From Yahoo Messenger...", "yahoo", this._cmdButtonListener, COMMAND_IMPORT_FROM_YAHOO));
		importMenu.add(UIUtils.makeMenuItem("From Gaim/Pidgin...", "pidgin", this._cmdButtonListener, COMMAND_IMPORT_FROM_GAIM));
		importMenu.add(UIUtils.makeMenuItem("From Digsby...", "digsby", this._cmdButtonListener, COMMAND_IMPORT_FROM_DIGSBY));
		importMenu.add(UIUtils.makeMenuItem("From MSN Messenger...", "msn", this._cmdButtonListener, COMMAND_IMPORT_FROM_MSN));
		
		return importMenu;
	}
	
	/**
	 * Creates the Export submenu.
	 * 
	 * @return The created JMenu
	 */
	protected JMenu _createExportSubmenu()
	{	
		JMenu exportMenu = UIUtils.makeMenu("Export", "archive+ovl_out");
		exportMenu.add(UIUtils.makeMenuItem("To File...", "drive", this._cmdButtonListener, COMMAND_EXPORT_TO_FILE));
		
		return exportMenu;
	}
	
	/**
	 * Creates the Operations menu.
	 * 
	 * @return The created JMenu
	 */
	protected JMenu _createOperationsMenu()
	{	
		this._menuItemAddGroup = UIUtils.makeMenuItem("Create Group...", "group+ovl_add", this._cmdButtonListener, COMMAND_CREATE_GROUP);
		this._menuItemAddContact = UIUtils.makeMenuItem("Create Contact...", "contact+ovl_add", this._cmdButtonListener, COMMAND_CREATE_CONTACT);
		this._menuItemAddIdentity = UIUtils.makeMenuItem("Create Identity...", "identity+ovl_add", this._cmdButtonListener, COMMAND_CREATE_IDENTITY);
		this._menuItemDeleteSelected = UIUtils.makeMenuItem("Delete Selected", "delete", this._cmdButtonListener, COMMAND_DELETE_SELECTED);
		this._menuItemRenameSelected = UIUtils.makeMenuItem("Rename Selected", "rename", this._cmdButtonListener, COMMAND_RENAME_SELECTED);
		this._menuItemMoveSelected = UIUtils.makeMenuItem("Move Selected", "move", this._cmdButtonListener, COMMAND_MOVE_SELECTED);
		this._menuItemMergeSelected = UIUtils.makeMenuItem("Merge Selected", "merge", this._cmdButtonListener, COMMAND_MERGE_SELECTED);
		
		JMenu opsMenu = new JMenu("Operations");
		opsMenu.add(this._menuItemAddGroup);
		opsMenu.add(this._menuItemAddContact);
		opsMenu.add(this._menuItemAddIdentity);
		opsMenu.add(new JSeparator());
		opsMenu.add(this._menuItemDeleteSelected);
		opsMenu.add(this._menuItemRenameSelected);
		opsMenu.add(this._menuItemMoveSelected);
		opsMenu.add(this._menuItemMergeSelected);
		
		return opsMenu;
	}
	
	/**
	 * Creates the Search menu.
	 * 
	 * @return The created JMenu
	 */
	protected JMenu _createSearchMenu()
	{	
		this._menuItemFind = UIUtils.makeMenuItem("Find In Conversation...", "Ctrl+F", "find", this._cmdButtonListener, COMMAND_FIND_FIRST);
		this._menuItemFindNext = UIUtils.makeMenuItem("Find Next Occurence", "F3", "occurence+ovl_next", this._cmdButtonListener, COMMAND_FIND_NEXT);
		this._menuItemFindPrev = UIUtils.makeMenuItem("Find Previous Occurence", "Shift+F3", "occurence+ovl_prev", this._cmdButtonListener, COMMAND_FIND_PREV);
		this._menuItemClearFind = UIUtils.makeMenuItem("Finish Search", "find+ovl_delete", this._cmdButtonListener, COMMAND_CLEAR_FIND);
		
		JMenu sfMenu = UIUtils.makeMenu("Search", null);
		sfMenu.add(UIUtils.makeMenuItem("Search Conversations...", "Ctrl+H", "search", this._cmdButtonListener, COMMAND_SEARCH));
		sfMenu.add(new JSeparator());
		sfMenu.add(this._menuItemFind);
		sfMenu.add(this._menuItemFindNext);
		sfMenu.add(this._menuItemFindPrev);
		sfMenu.add(this._menuItemClearFind);
		
		return sfMenu;
	}
	
	/**
	 * Initializes the main panel and all child controls.
	 * 
	 * @return A newly created JPanel
	 */
	protected JPanel _createMainPanel()
	{
		// Create the three main view controls
		this._groupsView = new ArchiveGroupsView();
		this._conversationsView = new ConversationsView();
		this._chatView = new ChatView();
		
		// Create scrolling panes and size them appropriately
		int em = this._groupsView.getFont().getSize();
		JScrollPane contactsPane = new JScrollPane(this._groupsView);
		contactsPane.setPreferredSize(new Dimension(em*22, 100));
		contactsPane.setMinimumSize(new Dimension(em*22, 100));
		
		this._conversationsView.setPreferredSize(new Dimension(em*28, 100));
		this._conversationsView.setMinimumSize(new Dimension(em*28, 100));
		
		// Initialize panel
		GridBagPanel panel = new GridBagPanel();
		panel.add(new ViewLabel("Contacts")       , 0, 0, 1, 1, 0.0, 0.0, panel.WEST  , panel.NONE ,  0, 0, 0, 4);
		panel.add(contactsPane                    , 0, 1, 1, 1, 0.0, 1.0, panel.CENTER, panel.BOTH ,  0, 0, 4, 0);
		panel.add(new ViewLabel("Conversations")  , 1, 0, 1, 1, 0.0, 0.0, panel.WEST  , panel.NONE ,  0, 0, 0, 4);
		panel.add(this._conversationsView         , 1, 1, 1, 1, 0.0, 1.0, panel.CENTER, panel.BOTH ,  0, 0, 4, 0);
		panel.add(new ViewLabel("Chat")           , 2, 0, 1, 1, 1.0, 0.0, panel.WEST  , panel.NONE ,  0, 0, 0, 4);
		panel.add(this._chatView                  , 2, 1, 1, 1, 1.0, 1.0, panel.CENTER, panel.BOTH ,  0, 0, 0, 0);
		panel.setBorder(new EmptyBorder(8,8,8,8));
		
		// Add listeners
		this._groupsView.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
					@Override
					public void valueChanged(TreeSelectionEvent ev) { _onGroupsSelectionChanged(); }	
				});
		
		this._conversationsView.addListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent ev) { _onConversationsViewEvent(ev.getActionCommand()); }
		});
		
		this._chatView.addListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent ev) { _onChatViewEvent(ev.getActionCommand()); }
		});
		
		return panel;
	}
	
	/**
	 * Sets an archive as the 'current' archive, refreshing
	 * controls if necessary.
	 * 
	 * @param archive The archive that will become current
	 */
	protected void _setArchive(IMArchive archive)
	{
		if (this._archive != null) this._archive.removeListener(this._archiveListener);
		this._archive = archive;
		if (this._archive != null) this._archive.addListener(this._archiveListener);
		this._groupsView.setArchive(archive);
		this._conversationsView.setArchive(archive);
		
		this._updateFileMenu();
		this._updateOperationsMenu();
		this._updateSearchMenu();
		this._runButtonEnableLogic();
	}
	
	/**
	 * Reacts to the closing of the window (by disposing it)
	 */
	protected void _onClose()
	{
		this._gaimImportForm.dispose();
		this._yahooImportForm.dispose();
		this._digsbyImportForm.dispose();
		this._msnImportForm.dispose();
		
		this._progressDialog.dispose();
		this._newArchiveDialog.dispose();
		this._importFinishedDialog.dispose();
	
		ArchiveDb.getInstance().close();
		this.dispose();
	}
	
	/**
	 * Reacts to a change in the selection for the "Groups"
	 * view.
	 */
	protected void _onGroupsSelectionChanged()
	{
		this._updateOperationsMenu();
		this._updateConversationsViewFilter();
	}
	
	/**
	 * Reacts to an event in the Conversations View.
	 * 
	 * @param eventName A ConversationsView.ACTION_* constant describing the
	 *                  event semantics
	 */
	protected void _onConversationsViewEvent(String eventName)
	{
		if (eventName.equals(ConversationsView.ACTION_SELECTION_CHANGED))
		{
			this._chatView.setConversation(this._conversationsView.getSelectedConversation());
		}
		else if (eventName.equals(ConversationsView.ACTION_SEARCH_FINISHED))
		{
			this._chatView.doFindFirst(this._conversationsView.getLastSearchString());
		}
	}
	
	/**
	 * Reacts to an event in the Chat View.
	 * 
	 * @param eventName A ChatView.ACTION_* constant describing the
	 *                  event semantics
	 */
	protected void _onChatViewEvent(String eventName)
	{
		this._updateSearchMenu();
	}

	/**
	 * Executes the "new archive..." command.
	 */
	protected void _doNewArchive()
	{
		String name = this._newArchiveDialog.showDialog();
		if (name == null) return;
		
		try
		{
			// Create new archive
			IMArchive newArchive = new IMArchive(name);
			newArchive.createGroup("Default");
			this._updateArchivesList();
			
			// Connect to new archive
			this._doConnectToArchive(name);
		}
		catch (Exception e)
		{
			this._showErrorMessage("Cannot create new archive:\n"+e.getMessage());
		}
	}
	
	/**
	 * Executes the Connect to Archive command.
	 * 
	 * @param archiveName The name of the archive to connect to
	 */
	protected void _doConnectToArchive(String archiveName)
	{
		try
		{
			this._setArchive(new IMArchive(archiveName));
		}
		catch (Exception e)
		{
			this._showErrorMessage("Cannot connect to archive:\n"+e.getMessage());
		}
	}
	
	/**
	 * Executes the Disconnect command.
	 */
	protected void _doDisconnect()
	{
		this._setArchive(null);
	}
	
	/**
	 * Executes one of the Import Archive commands that
	 * depends on an import dialog.
	 * 
	 * @param dialog The import dialog to open
	 */
	protected void _doArchiveImport(ImportDialog dialog)
	{
		dialog.reset();
		dialog.setVisible(true);
		if (dialog.archive != null) this._finishImport(dialog.archive);
	}
	
	/**
	 * Executes the Import Archive From File command.
	 */
	protected void _doImportArchiveFromFile()
	{
		this._fileChooser.setDialogTitle("Import IM Archive");
		if (this._fileChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
		File file = this._fileChooser.getSelectedFile();
		
		// Prepare and execute task
		TaskWorker taskWorker = new TaskWorker(new LoadArchiveTask(file));
		Object result = taskWorker.runTask();
		
		if (result instanceof Exception)
		{
			this._showErrorMessage("Could not import archive:\n"+((Exception)result).getMessage());
			return;
		}
		
		this._finishImport((IMArchive)result);
	}
	
	/**
	 * Executes the Import From Archive command.
	 * 
	 * @param archiveName The name of the archive to import from
	 */
	protected void _doImportFromArchive(String archiveName)
	{
		IMArchive archive;

		try
		{			
			archive = new IMArchive(archiveName);
		}
		catch (Exception e)
		{
			this._showErrorMessage("Cannot connect to archive:\n"+e.getMessage());
			return;
		}
		
		this._finishImport(archive);
	}
	
	/**
	 * Executes the Export Archive to File command.
	 */
	protected void _doExportArchiveToFile()
	{
		this._exportArchiveToFile(this._archive, null);
	}
	
	/**
	 * Executes the Create Group command.
	 */
	protected void _doCreateGroup()
	{
		this._groupsView.doAddGroup();
	}
	
	/**
	 * Executes the Create Contact command.
	 */
	protected void _doCreateContact()
	{
		if (this._archive.getRegularGroups().isEmpty()) return;
		
		this._groupsView.doAddContact(this._archive.getRegularGroups().get(0));
	}
	
	/**
	 * Executes the Create Identity command.
	 */
	protected void _doCreateIdentity()
	{
		this._groupsView.doAddIdentity();
	}
	
	/**
	 * Executes the Delete Selected command.
	 */
	protected void _doDeleteSelected()
	{
		this._groupsView.doDeleteSelected();
	}
	
	/**
	 * Executes the Rename Selected command.
	 */
	protected void _doRenameSelected()
	{
		this._groupsView.doRenameSelected();
	}
	
	/**
	 * Executes the Move Selected command.
	 */
	protected void _doMoveSelected()
	{
		this._groupsView.doMoveSelected();
	}
	
	/**
	 * Executes the Merge Selected command.
	 */
	protected void _doMergeSelected()
	{
		this._groupsView.doMergeSelected();
	}
	
	/**
	 * Executes the Search command as issued from
	 * the main menu.
	 */
	protected void _doSearch()
	{
		String searchString = (String)JOptionPane.showInputDialog(this,
				"Input the terms to search for:",
				"Search In Conversations",
				JOptionPane.PLAIN_MESSAGE,
				IconManager.getInstance().getIcon("kibble/search"),
				null,
				this._conversationsView.getLastSearchString());
		
		if (searchString == null) return;
		
		this._conversationsView.doSearch(searchString);
	}
	
	/**
	 * Executes the Find First command.
	 */
	protected void _doFindFirst()
	{
		String searchString = (String)JOptionPane.showInputDialog(this,
				"Input the terms to search for:",
				"Find In Conversation",
				JOptionPane.PLAIN_MESSAGE,
				IconManager.getInstance().getIcon("kibble/search"),
				null,
				"");
		
		if (searchString == null) return;
		
		this._chatView.doFindFirst(searchString);
	}
	
	/**
	 * Executes the Find Next command.
	 */
	protected void _doFindNext()
	{
		this._chatView.doFindNext();
	}
	
	/**
	 * Executes the Find Prev command.
	 */
	protected void _doFindPrev()
	{
		this._chatView.doFindPrev();
	}
	
	/**
	 * Executes the Clear Find command.
	 */
	protected void _doClearFind()
	{
		this._chatView.doClearFind();
	}
	
	/**
	 * Executes a search for a given expression in the
	 * conversations currently visible in the Conversations View.
	 * 
	 * @param searchString The search expression. This will be
	 *                     converted intelligently to a regular
	 *                     expression
	 */
	protected void _searchConversations(String searchString)
	{
		this._conversationsView.doSearch(searchString);
	}
	
	/**
	 * Constructs a regular expression corresponding to
	 * the user-supplied terms in the search field.
	 * 
	 * @param searchString The user-supplied search string
	 * @return A regular expression, or null if the search
	 *         string is in some way invalid.
	 */
	protected Pattern _compileSearchString(String searchString)
	{		
		String[] tokens = searchString.trim().split("\\s+");
		if ((tokens.length == 0) || (tokens[0].isEmpty())) return null;
		
		StringBuilder buf = new StringBuilder();
		for (String token : tokens)
		{
			if (buf.length() > 0) buf.append("(?:\\s+|(?:\\S*\\s+\\S*)|.*)");
			buf.append(Pattern.quote(token));
		}
		
		return Pattern.compile(buf.toString(), Pattern.CASE_INSENSITIVE);
	}
	
	/**
	 * Prompts the user for the destination of recently
	 * imported archive data, and finishes up the
	 * import.
	 * 
	 * @param archive A temporary archive containing the
	 *                imported data. It will be destroyed
	 *                at the end of the function.
	 */
	protected void _finishImport(IMArchive archive)
	{
		try
		{
			// Show the Import Finished dialog and allow the user to select
			// an action and a destination
			ImportFinishedDialog.Action action = this._importFinishedDialog.showDialog(archive, (this._archive != null) ? this._archive.getName() : null);
			if (action == ImportFinishedDialog.Action.CANCELLED) return;
			boolean merge = (action == ImportFinishedDialog.Action.MERGE_INTO);
			
			// Connect to the destination archive
			IMArchive destination;
			String destName = this._importFinishedDialog.resultArchiveName;
				
			if ((this._archive != null) && this._archive.getName().equals(destName))
				destination = this._archive;
			else
				destination = new IMArchive(destName);
			this._updateArchivesList();
	
			// Prepare and execute task
			TaskWorker taskWorker = new TaskWorker(new CopyOrMergeArchiveTask(archive, destination,
					merge, this._importFinishedDialog.resultAccountingOnly));
			Object result = taskWorker.runTask();
			
			// Handle errors
			if (result instanceof Exception)
			{
				this._showErrorMessage("Could not "+(merge ? "merge" : "copy")+" archive data:\n"+
						((Exception)result).getMessage());
				return;
			}
			
			// Delete the source archive, if it was temporary
			if (archive.isTemporary())
			{
				new TaskWorker(new DeleteArchiveTask(archive)).runTask();
			}
			
			if (this._importFinishedDialog.resultConnectAfter)
				if (destination != this._archive) this._setArchive(destination);
		}
		catch (Exception e)
		{
			this._showErrorMessage("Cannot import archive:\n"+e.getMessage());
		}
	}
	
	/**
	 * Exports an archive to a given file.
	 * 
	 * @param archive The archive to export
	 * @param file The file to which the archive will be exported.
	 *             If this is null, the user will be prompted for
	 *             a file.
	 */
	protected void _exportArchiveToFile(IMArchive archive, File file)
	{
		// Prompt the user for a file name if necessary
		while (file == null)
		{
			// Show the Save dialog
			this._fileChooser.setDialogTitle("Export IM Archive");
			if (this._fileChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
		
			// Get file, adjusting the extension if necessary
			file = this._fileChooser.getSelectedFile();
			if (!file.getName().toLowerCase().endsWith(".json")) file = new File(file.getPath()+".json");
		
			// Check for overwriting
			if (file.exists())
				if (JOptionPane.showConfirmDialog(this, "File already exists. Overwrite?",
						"Confirm Overwrite", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE)
						!= JOptionPane.YES_OPTION)
					file = null;
		}
		
		// Prepare and execute task
		TaskWorker taskWorker = new TaskWorker(new ExportArchiveToFileTask(archive, file));
		Object result = taskWorker.runTask();
		
		if (result instanceof Exception)
		{
			this._showErrorMessage("Could not export archive:\n"+((Exception)result).getMessage());
			return;
		}
	}
	
	/**
	 * Updates the list of archives known by the main form
	 * by requerying the archive database.
	 */
	protected void _updateArchivesList()
	{
		try
		{
			this._archives = ArchiveDb.getInstance().getArchives();
			this._updateFileMenu();
		}
		catch (Exception e)
		{
			this._showErrorMessage("Cannot query list of archives:\n"+e.getMessage());
			return;
		}
	}
	
	/**
	 * Updates the file menu so as to reflect possible
	 * operations given the currently selected archive (if any).
	 */
	protected void _updateFileMenu()
	{
		boolean haveArchive = (this._archive != null);
		
		this._menuItemDisconnect.setEnabled(haveArchive);
		this._menuExport.setEnabled(haveArchive);
		
		this._updateConnectSubmenu();
		this._updateImportFromArchiveSubmenu();
	}
	
	/**
	 * Updates the Connect submenu to reflect the list
	 * of known existing archives.
	 */
	protected void _updateConnectSubmenu()
	{
		this._menuConnect.setEnabled(!this._archives.isEmpty());
		this._menuConnect.removeAll();
		
		for (String arcName : this._archives)
		{
			boolean selected = ((this._archive != null) && (this._archive.getName().equals(arcName)));
			
			this._menuConnect.add(UIUtils.makeMenuItem(arcName+(selected ? " (connected)" : ""),
					selected ? "archive_current" : "archive",
					this._cmdButtonListener, selected ? COMMAND_NOP : COMMAND_CONNECT));
		}
	}
	
	/**
	 * Updates the Import From Archive submenu to reflect the list
	 * of known existing archives.
	 */
	protected void _updateImportFromArchiveSubmenu()
	{
		this._menuImportFromArchive.setEnabled(!this._archives.isEmpty());
		this._menuImportFromArchive.removeAll();
		
		for (String arcName : this._archives)
		{
			boolean selected = ((this._archive != null) && (this._archive.getName().equals(arcName)));
			
			this._menuImportFromArchive.add(UIUtils.makeMenuItem(arcName,
					selected ? "archive_current" : "archive",
					this._cmdButtonListener, COMMAND_IMPORT_FROM_ARCHIVE));
		}
	}
	
	/**
	 * Updates the operations menu so as to reflect possible
	 * operations given the current selection in the Groups View.
	 */
	protected void _updateOperationsMenu()
	{
		if (this._archive == null)
		{
			this._menuOperations.setEnabled(false);
			return;
		}
		
		this._menuOperations.setEnabled(true);
		
		this._menuItemAddContact.setEnabled(!this._archive.getRegularGroups().isEmpty());
		
		String selName = this._groupsView.getSelectionName();
		this._menuItemDeleteSelected.setText("Delete "+selName);
		this._menuItemDeleteSelected.setEnabled(this._groupsView.canDeleteSelection());
		
		this._menuItemRenameSelected.setText("Rename "+selName);
		this._menuItemRenameSelected.setEnabled(this._groupsView.canRenameSelection());
		
		this._menuItemMoveSelected.setText("Move "+selName+"...");
		this._menuItemMoveSelected.setEnabled(this._groupsView.canMoveSelection());
		
		this._menuItemMergeSelected.setText("Merge "+selName+"...");
		this._menuItemMergeSelected.setEnabled(this._groupsView.canMergeSelection());
	}
	
	/**
	 * Updates the Search menu so as to reflect possible
	 * operations given the current selection in the Chat
	 * view and the presence of an archive.
	 */
	protected void _updateSearchMenu()
	{
		if (this._archive == null)
		{
			this._menuSearch.setEnabled(false);
			return;
		}
		
		this._menuItemFind.setEnabled(this._chatView.hasConversation());
		this._menuItemFindNext.setEnabled(this._chatView.hasFindMark());
		this._menuItemFindPrev.setEnabled(this._chatView.hasFindMark());
		this._menuItemClearFind.setEnabled(this._chatView.hasFindTerms());
		
		this._menuSearch.setEnabled(true);
	}
	
	/**
	 * Enables or disables controls in the main form according
	 * to the current situation.
	 */
	protected void _runButtonEnableLogic()
	{
	}
	
	/**
	 * Filters the conversations in the Conversation View
	 * according to the selected items in the Groups View.
	 */
	protected void _updateConversationsViewFilter()
	{
		List<Group> groups = new ArrayList<Group>();
		List<Contact> contacts = new ArrayList<Contact>();
		List<Account> accounts = new ArrayList<Account>();
		
		for (Object item : this._groupsView.getSelection())
		{
			if (item instanceof Group) { groups.add((Group)item); }
			else if (item instanceof Contact) { contacts.add((Contact)item); }
			else if (item instanceof Account) { accounts.add((Account)item); }
		}
		
		ConversationsQuery query = this._conversationsView.getQuery();
		query.filterContacts = contacts;
		query.filterGroups = groups;
		query.filterAccounts = accounts;
		query.filterConversations.clear();
		this._conversationsView.setQuery(query);
	}
	
	/**
	 * Convenience function for showing a modal error message.
	 */
	protected void _showErrorMessage(String message)
	{
		JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
	}
	
	/**
	 * Internal class that responds to changes in the archive.
	 */
	protected class ArchiveListener implements IMArchiveListener
	{
		@Override
		public void archiveChanged(IMArchiveEvent event)
		{
			switch (event.type)
			{
			case DELETED_ITEMS:
				// If the archive has been deleted, disconnect
				if (!event.isEmpty() && (event.items.get(0) instanceof IMArchive))
				{
					_updateArchivesList();
					_doDisconnect();
					return;
				}
				// Intentional fallthrough
			case ADDED_ITEMS:
			case MAJOR_CHANGE:
				_updateOperationsMenu();
				break;
			}
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
				
				if (commandId.equals(MainForm.COMMAND_NEW_ARCHIVE)) { _doNewArchive(); }
				else if (commandId.equals(MainForm.COMMAND_CONNECT)) { _doConnectToArchive(((JMenuItem)evt.getSource()).getText()); }
				else if (commandId.equals(MainForm.COMMAND_DISCONNECT)) { _doDisconnect(); }
				else if (commandId.equals(MainForm.COMMAND_END_PROGRAM)) { _onClose(); }
				else if (commandId.equals(MainForm.COMMAND_IMPORT_FROM_FILE)) { _doImportArchiveFromFile(); }
				else if (commandId.equals(MainForm.COMMAND_IMPORT_FROM_ARCHIVE)) { _doImportFromArchive(((JMenuItem)evt.getSource()).getText()); }
				else if (commandId.equals(MainForm.COMMAND_IMPORT_FROM_GAIM)) { _doArchiveImport(_gaimImportForm); }
				else if (commandId.equals(MainForm.COMMAND_IMPORT_FROM_YAHOO)) { _doArchiveImport(_yahooImportForm); }
				else if (commandId.equals(MainForm.COMMAND_IMPORT_FROM_DIGSBY)) {  _doArchiveImport(_digsbyImportForm); }
				else if (commandId.equals(MainForm.COMMAND_IMPORT_FROM_MSN)) {  _doArchiveImport(_msnImportForm); }
				else if (commandId.equals(MainForm.COMMAND_EXPORT_TO_FILE)) { _doExportArchiveToFile(); }
				else if (commandId.equals(MainForm.COMMAND_CREATE_GROUP)) { _doCreateGroup(); }
				else if (commandId.equals(MainForm.COMMAND_CREATE_CONTACT)) { _doCreateContact(); }
				else if (commandId.equals(MainForm.COMMAND_CREATE_IDENTITY)) { _doCreateIdentity(); }
				else if (commandId.equals(MainForm.COMMAND_DELETE_SELECTED)) { _doDeleteSelected(); }
				else if (commandId.equals(MainForm.COMMAND_RENAME_SELECTED)) { _doRenameSelected(); }
				else if (commandId.equals(MainForm.COMMAND_MOVE_SELECTED)) { _doMoveSelected(); }
				else if (commandId.equals(MainForm.COMMAND_MERGE_SELECTED)) { _doMergeSelected(); }
				else if (commandId.equals(MainForm.COMMAND_SEARCH)) { _doSearch(); }
				else if (commandId.equals(MainForm.COMMAND_FIND_FIRST)) { _doFindFirst(); }
				else if (commandId.equals(MainForm.COMMAND_FIND_NEXT)) { _doFindNext(); }
				else if (commandId.equals(MainForm.COMMAND_FIND_PREV)) { _doFindPrev(); }
				else if (commandId.equals(MainForm.COMMAND_CLEAR_FIND)) { _doClearFind(); }
			}
		}
	}
	
	/**
	 * Internal class for a Swing worker running potentially
	 * long-duration tasks such as saving and loading archives.
	 * A progress dialog will be shown for the duration of the
	 * task.
	 */
	protected class TaskWorker extends SwingWorker<Object,Object> implements ProgressListener
	{
		protected Object _returnValue;
		protected Task _task;
		
		/**
		 * Constructor.
		 */
		public TaskWorker(Task task)
		{
			this._task = task;
		}
		
		/**
		 * This function should be called from the Swing event dispatch
		 * thread to execute a job while displaying a modal progress
		 * dialog.
		 * 
		 * Note: this function will block until the task is complete.
		 */
		public Object runTask()
		{
			this.execute();
			_progressDialog.setProgress(new ProgressEvent(" ",0,-1));
			_progressDialog.popup();
			
			return this._returnValue;
		}
		
		/**
		 * Executes the worker's actual job.
		 * 
		 * This procedure executes in the worker's private
		 * thread.
		 */
		@Override
		protected Object doInBackground() throws Exception
		{
			try
			{
				this._returnValue = this._task.run(this);
			}
			catch (Exception e)
			{
				this._returnValue = e;
			}
			
			return this._returnValue;
		}
		
		@Override
		/**
		 * Executes GUI operations at the end of this worker's task.
		 * 
		 * This procedure executes in the Swing event dispatch thread. 
		 */
		protected void done()
		{
			_progressDialog.close();
		}

		@Override
		/**
		 * Reacts to events published by the running job.
		 * 
		 * This procedure executes in the Swing event dispatch thread.
		 */
		protected void process(List<Object> events)
		{
			for (Object event : events)
			{
				if (event instanceof ProgressEvent)
				{
					_progressDialog.setProgress((ProgressEvent)event);
				}
			}
		}
		
		@Override
		/**
		 * Reacts to progress in the task.
		 * 
		 * This procedure executes in the worker's private
		 * thread.
		 */
		public void onProgress(ProgressEvent ev)
		{
			this.publish(ev);	
		}
	}
	
	/**
	 * Internal interface for defining a general task
	 * that can be run by the TaskWorker, such as
	 * loading or saving archives, etc.
	 */
	protected interface Task
	{
		/**
		 * Runs this task inside the TaskWorker.
		 * 
		 * @param progressListener A reference to an object
		 *                         that listens to progress in
		 *                         the task. May be null.
		 * @return The return value for this task
		 */
		public Object run(ProgressListener progressListener) throws Exception;
	}
	
	/**
	 * Class for defining a "load archive" task.
	 */
	protected static class LoadArchiveTask implements Task
	{
		protected File _file;
		
		/**
		 * Constructor.
		 * 
		 * @param file The file from which the archive
		 *             is to be loaded.
		 */
		public LoadArchiveTask(File file)
		{
			this._file = file;
		}
		
		@Override
		public Object run(ProgressListener progressListener) throws Exception
		{
			IMArchiveJsonReader reader = new IMArchiveJsonReader(this._file);
			
			return reader.readArchive(progressListener);
		}
	}
	
	/**
	 * Class for defining a "export archive to file" task.
	 */
	protected static class ExportArchiveToFileTask implements Task
	{
		protected IMArchive _archive;
		protected File _file;
		
		/**
		 * Constructor.
		 * 
		 * @param archive The archive to be exported
		 * @param file The file to which the archive is to
		 *             be exported.
		 */
		public ExportArchiveToFileTask(IMArchive archive, File file)
		{
			this._archive = archive;
			this._file = file;
		}
		
		@Override
		public Object run(ProgressListener progressListener) throws Exception
		{
			IMArchiveJsonWriter writer = new IMArchiveJsonWriter(this._file);
			writer.writeArchive(this._archive, progressListener);
			writer.close();
			
			return null;
		}	
	}
	
	/**
	 * Class for defining a "copy/merge archive" task.
	 */
	protected static class CopyOrMergeArchiveTask implements Task
	{
		protected IMArchive _srcArchive;
		protected IMArchive _destArchive;
		protected boolean _merge;
		protected boolean _accountingOnly;
		
		/**
		 * Constructor.
		 * 
		 * @param srcArchive The archive to be copied/merged
		 * @param destArchive The receiving archive
		 * @param merge True if a merge is desired, false if
		 *              we want a full replacement
		 * @param accountingOnly True if we only wish to merge/copy
		 *                       the accounting data (groups, contacts and
		 *                       accounts)
		 */
		public CopyOrMergeArchiveTask(IMArchive srcArchive, IMArchive destArchive, boolean merge,
				boolean accountingOnly)
		{
			this._srcArchive = srcArchive;
			this._destArchive = destArchive;
			this._merge = merge;
			this._accountingOnly = accountingOnly;
		}
		
		@Override
		public Object run(ProgressListener progressListener) throws Exception
		{
			if (this._merge)
				this._destArchive.mergeData(this._srcArchive, this._accountingOnly, progressListener);
			else
				this._destArchive.replaceData(this._srcArchive, this._accountingOnly, progressListener);
			
			return null;
		}	
	}
	
	/**
	 * Class for defining a "delete archive" task.
	 */
	protected static class DeleteArchiveTask implements Task
	{
		protected IMArchive _archive;
		
		/**
		 * Constructor.
		 * 
		 * @param archive The archive to be deleted
		 */
		public DeleteArchiveTask(IMArchive archive)
		{
			this._archive = archive;
		}
		
		@Override
		public Object run(ProgressListener progressListener) throws Exception
		{
			if (progressListener != null)
				progressListener.onProgress(
						new ProgressEvent(this._archive.isTemporary() ?
							"Cleaning up..." : ("Deleting archive "+this._archive.getName()+"'..."),
							0, -1));
			
			this._archive.delete();
			
			return null;
		}	
	}
}
