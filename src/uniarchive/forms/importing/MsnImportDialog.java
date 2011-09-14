/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.forms.importing;

import java.awt.Dimension;
import java.awt.Window;
import java.io.File;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import uniarchive.models.archive.IMArchive;
import uniarchive.models.import_common.Alias;
import uniarchive.models.import_common.ConfirmLocalNamesQuery;
import uniarchive.models.import_common.Feedback;
import uniarchive.models.import_common.ImportMessage;
import uniarchive.models.import_common.OperationStatus;
import uniarchive.models.import_common.UnresolvedAliasesQuery;
import uniarchive.models.msn_import.MsnImportJob;
import uniarchive.widgets.GridBagPanel;
import uniarchive.widgets.UIUtils;
import uniarchive.widgets.importing.NameList;
import uniarchive.widgets.importing.UnresolvedAliasesTable;

/**
 * Class for the MSN Messenger Import wizard dialog.
 */
public class MsnImportDialog extends ImportDialog
{
	private static final long serialVersionUID = 1;
	
	protected static final String COMMAND_TRANSFER_LEFT = "transferLeft";
	protected static final String COMMAND_TRANSFER_RIGHT = "transferRight";
	
	protected static final String PAGE_LOCAL_NAMES_QUERY = "localNamesQuery";
	protected static final String PAGE_UNRESOLVED_ALIASES_QUERY = "aliasesQuery";
	
	protected NameList _localNamesList;
	protected NameList _remoteNamesList;
	protected UnresolvedAliasesTable _unresolvedAliasesTable;
	
	/**
	 * Constructor.
	 * 
	 * @param owner The parent window for this dialog
	 */
	public MsnImportDialog(Window owner)
	{
		super(owner, "MSN Messenger");
	}
	
	/**
	 * Initializes the main panel and all child controls.
	 * 
	 * @return A newly created JPanel
	 */
	@Override
	protected JPanel _createMainPanel()
	{
		this._mainPanel = super._createMainPanel();
		this._mainPanel.add(this._createLocalNamesQueryPage(), PAGE_LOCAL_NAMES_QUERY);
		this._mainPanel.add(this._createUnresolvedAliasesQueryPage(), PAGE_UNRESOLVED_ALIASES_QUERY);
		
		return this._mainPanel;
	}
	
	/**
	 * Gets the help text for the "locate archive" page. This may
	 * have to be personalized for each individual archive type.
	 * 
	 * @return The help text (in HTML format)
	 */
	@Override
	protected String _getLocateArchiveHelpText()
	{
		return "Please select the location of the MSN Messenger archive directory. This "+
			"folder usually contains a number of .xml files, one for each of your " +
			"contacts.";
	}
	
	/**
	 * Creates the "local names query" page panel
	 * 
	 * @return A newly created JPanel
	 */
	protected JPanel _createLocalNamesQueryPage()
	{
		JLabel helpLabel = new JLabel("<html>The import process requires your guidance in determining which "+
			"names identify the local user (you) in the conversations. A preliminary list, shown in the left panel, "+
			"has been determined using heuristics. Please review both lists <b>thoroughly</b> and make corrections if "+
			"necessary by using the buttons or drag-and-drop to move names between the two lists.</html>");
		
		this._localNamesList = new NameList("person");
		this._remoteNamesList = new NameList("contact");
		
		int fontSize = this._localNamesList.getFont().getSize();
		Dimension dimension = new Dimension(fontSize*16, fontSize*8);

		JScrollPane localNamesScroller = new JScrollPane(this._localNamesList);
		localNamesScroller.setMinimumSize(dimension);
		JScrollPane remoteNamesScroller = new JScrollPane(this._remoteNamesList);
		remoteNamesScroller.setMinimumSize(dimension);
		
		Box transButtonsPanel = new Box(BoxLayout.Y_AXIS);
		transButtonsPanel.add(Box.createVerticalGlue());
		transButtonsPanel.add(UIUtils.makeButton(null, "transfer_right", this._cmdButtonListener, COMMAND_TRANSFER_RIGHT));
		transButtonsPanel.add(UIUtils.makeButton(null, "transfer_left",  this._cmdButtonListener, COMMAND_TRANSFER_LEFT));
		transButtonsPanel.add(Box.createVerticalGlue());
		
		GridBagPanel page = new GridBagPanel();
		page.add(helpLabel,                   0, 0, 5, 1, 1.0, 0.0, page.WEST,   page.BOTH, 0, 0, 0, 8);
		page.add(new JPanel(),                0, 1, 1, 2, 0.25, 1.0, page.CENTER, page.BOTH);
		page.add(new JLabel("Local names:"),  1, 1, 2, 1, 0.25, 0.0, page.WEST,   page.NONE, 0, 0, 0, 4);
		page.add(new JLabel("Remote names:"), 3, 1, 1, 1, 0.25, 0.0, page.WEST,   page.NONE, 0, 0, 0, 4);
		page.add(new JPanel(),                4, 1, 1, 2, 0.25, 1.0, page.CENTER, page.BOTH);
		page.add(localNamesScroller,          1, 2, 1, 1, 0.25, 0.0, page.CENTER, page.BOTH);
		page.add(transButtonsPanel,           2, 2, 1, 1, 0.0, 0.0, page.CENTER, page.BOTH, 8, 0, 8, 0);
		page.add(remoteNamesScroller,         3, 2, 1, 1, 0.25, 0.0, page.CENTER, page.BOTH);
		
		return page;
	}
	
	/**
	 * Creates the "unresolved aliases query" page panel
	 * 
	 * @return A newly created JPanel
	 */
	protected JPanel _createUnresolvedAliasesQueryPage()
	{
		JLabel helpLabel = new JLabel("<html>Your assistance is required in resolving the following "
				+"aliases to MSN accounts. You can select matching accounts from a list of guesses, or "
				+"create new accounts if necessary.</html>");
		
		this._unresolvedAliasesTable = new UnresolvedAliasesTable();
		JScrollPane tableScroller = new JScrollPane(this._unresolvedAliasesTable);
		
		GridBagPanel page = new GridBagPanel();
		page.add(helpLabel,                   0, 0, 1, 1, 1.0, 0.0, page.WEST,   page.BOTH, 0, 0, 0, 8);
		page.add(tableScroller,               0, 1, 1, 1, 1.0, 1.0, page.CENTER, page.BOTH, 0, 0, 0, 0);
		
		return page;
	}
	
	/**
	 * Checks whether the 'back' button should be enabled
	 * given the current situation.
	 * 
	 * @return True if the button is to be enabled, false otherwise
	 */
	@Override
	protected boolean _shouldEnableBack()
	{
		if (this._currentPage.equals(PAGE_LOCAL_NAMES_QUERY)) return false;
		
		return super._shouldEnableBack();
	}
	
	/**
	 * Executes the "proceed" command.
	 */
	@Override
	protected void _doProceed()
	{
		if (this._currentPage.equals(PAGE_LOCAL_NAMES_QUERY))
		{
			if (this._localNamesList.getNames().isEmpty())
			{
				this._showErrorMessage("You must select at least one local name");
				return;
			}
			
			this._lockControls();
			this._importJob.answerQuery(this._localNamesList.getNames());
		}
		else if (this._currentPage.equals(PAGE_UNRESOLVED_ALIASES_QUERY))
		{
			List<Alias> aliases = this._unresolvedAliasesTable.getData();
			for (Alias alias : aliases)
				if (alias.isUnresolved())
				{
					this._showErrorMessage("You have not resolved the name '"+alias.name+"'");
					return;
				}
			
			this._lockControls();
			this._importJob.answerQuery(aliases);
		}
		else super._doProceed();
	}
	
	/**
	 * Executes the "transfer names to the right" command.
	 */
	protected void _doTransferRight()
	{
		List<String> names = this._localNamesList.getSelectedNames();
		
		this._remoteNamesList.insertNames(this._remoteNamesList.getModel().getSize(), names);
		this._localNamesList.removeNames(names);
	}
	
	/**
	 * Executes the "transfer names to the left" command.
	 */
	protected void _doTransferLeft()
	{
		List<String> names = this._remoteNamesList.getSelectedNames();
		
		this._localNamesList.insertNames(this._localNamesList.getModel().getSize(), names);
		this._remoteNamesList.removeNames(names);
	}
	
	/**
	 * Reacts to the pressing of a button.
	 * 
	 * @param commandId The command string associated with the button
	 */
	protected void _onCommand(String commandId)
	{
		if (commandId.equals(GaimImportDialog.COMMAND_TRANSFER_RIGHT)) { this._doTransferRight(); }
		else if (commandId.equals(GaimImportDialog.COMMAND_TRANSFER_LEFT)) { this._doTransferLeft(); }
		else super._onCommand(commandId);
	}
	
	/**
	 * Reacts to the occurence of a local names confirmation query
	 * issued by the currently running import job.
	 * 
	 * @param query An object describing the query
	 */
	protected void _onLocalNamesQuery(ConfirmLocalNamesQuery query)
	{
		this._localNamesList.setNames(query.localNames);
		this._remoteNamesList.setNames(query.remoteNames);
		
		this._switchPage(PAGE_LOCAL_NAMES_QUERY);
		this._unlockControls();
	}
	
	/**
	 * Reacts to the occurence of an unresolved aliases confirmation
	 * query issued by the currently running import job.
	 * 
	 * @param query An object describing the query
	 */
	protected void _onUnresolvedAliasesQuery(UnresolvedAliasesQuery query)
	{
		this._unresolvedAliasesTable.loadData(query.aliases);
		this._unresolvedAliasesTable.setAccounts(query.accounts);
		
		this._switchPage(PAGE_UNRESOLVED_ALIASES_QUERY);
		this._unlockControls();
		
		if (query.aliases.isEmpty()) this._doProceed();
	}
	
	/**
	 * Create an import worker specific to this type
	 * of archive.
	 * 
	 * @param archivePath The path to the archive
	 * @return A customized ImportWorker
	 */
	protected ImportWorker _createImportWorker(File archivePath)
	{
		return new MsnImportWorker(archivePath);
	}
	
	/**
	 * Internal class for a Swing worker running the import job.
	 */
	protected class MsnImportWorker extends ImportWorker
	{
		protected MsnImportJob _job;
		
		/**
		 * Constructor.
		 * 
		 * @param archivePath The path to the archive 
		 */
		public MsnImportWorker(File archivePath)
		{
			this._job = new MsnImportJob(archivePath, this);
		}
		
		/**
		 * Override this function to specify the actual import
		 * actions. You may call publish, _awaitAnswer and throw
		 * exceptions from here. The procedure must end by
		 * publishing the IMArchive object.
		 * 
		 * This procedure executes in the worker's private thread.
		 */
		@SuppressWarnings("unchecked")
		@Override
		protected void _doImport() throws Exception
		{
			ConfirmLocalNamesQuery query1 = null;
			UnresolvedAliasesQuery query2 = null;
			int step = 0;
			
			while (step < 3)
			{
				switch (step)
				{
				case 0:
					query1 = this._job.runPhase1();
					step++;
					break;
				case 1:
					this.publish(query1);
					List<String> answer1 = (List<String>)this._awaitAnswer();
					if (answer1 == null) return;
					query1 = query1.applyAnswer(answer1); // so that changes are visible if we return
					
					Feedback feedback2 = this._job.runPhase2(answer1);
					if (feedback2 instanceof OperationStatus)
					{
						this.publish(new ImportMessage(ImportMessage.Type.ERROR,
								"The list of local names is not valid:\n\n"+
								((OperationStatus)feedback2).errors.get(0)+"\n\n"+
								"Please review the list and try again."));
						break;
					}
					
					query2 = (UnresolvedAliasesQuery)feedback2;
					step++;
					break;
				case 2:
					this.publish(query2);
					Object answer2 = this._awaitAnswer();
					if (answer2 == null) return;
					if (answer2 == Boolean.FALSE)
					{
						// Back button pressed
						step = 1;
						break;
					}
					query2 = query2.applyAnswer((List<Alias>)answer2); // so that changes are visible if we return
					
					Object feedback3 = this._job.runPhase3((List<Alias>)answer2);
					if (feedback3 instanceof OperationStatus)
					{
						this.publish(new ImportMessage(ImportMessage.Type.ERROR,
								"The account resolution is not valid:\n\n"+
								((OperationStatus)feedback3).errors.get(0)+"\n\n"+
								"Please review the list and try again."));
						break;
					}
					
					this.publish((IMArchive)feedback3);
					step++;
					break;
				}
			}
		}
		
		/**
		 * Override this function to specify the actions to be
		 * taken upon the receipt of an event (via the publish
		 * function). Note that the receipt of:
		 * 
		 * - an archive
		 * - an import progress event
		 * - an import message
		 * 
		 * are handled automatically and do not involve this function.
		 * 
		 * @param event The event to be processed
		 */
		@Override
		protected void _processEvent(Object event)
		{
			if (event instanceof ConfirmLocalNamesQuery)
			{
				_onLocalNamesQuery((ConfirmLocalNamesQuery)event);
			}
			else if (event instanceof UnresolvedAliasesQuery)
			{
				_onUnresolvedAliasesQuery((UnresolvedAliasesQuery)event);
			}
		}
	}
}