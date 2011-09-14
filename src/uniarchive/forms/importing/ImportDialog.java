/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.forms.importing;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import uniarchive.graphics.IconManager;
import uniarchive.models.ProgressEvent;
import uniarchive.models.ProgressListener;
import uniarchive.models.archive.IMArchive;
import uniarchive.models.import_common.ImportMessage;
import uniarchive.widgets.GridBagPanel;
import uniarchive.widgets.HorizEtchedLine;
import uniarchive.widgets.UIUtils;

/**
 * Superclass for the archive import wizard dialogs.
 */
public abstract class ImportDialog extends JDialog
{
	private static final long serialVersionUID = 1L;
	
	protected static final String COMMAND_PROCEED = "proceed";
	protected static final String COMMAND_GO_BACK = "goBack";
	protected static final String COMMAND_CANCEL = "cancel";
	protected static final String COMMAND_BROWSE_ARCHIVE_FOLDER = "browseArchiveFolder";
	
	protected static final String PAGE_LOCATE_ARCHIVE = "locateArchive";
	protected static final String PAGE_IMPORT_PROGRESS = "importProgress";
	
	public IMArchive archive;
	
	protected ImportWorker _importJob;
	protected ActionListener _cmdButtonListener = new CommandButtonListener();
	
	protected String _archiveType;
	protected String _currentPage = PAGE_LOCATE_ARCHIVE;
	protected boolean _locked = false;
	
	protected JLabel _phaseLabel;
	protected JButton _proceedButton;
	protected JButton _backButton;
	protected JPanel _mainPanel;
	
	protected JTextField _archiveFolderField;
	protected JFileChooser _archiveFolderChooser;
	
	protected JLabel _progressLabel1;
	protected JLabel _progressLabel2;
	protected JProgressBar _progressBar;
	
	protected JRadioButton _saveArchiveRadio;
	protected JRadioButton _mergeArchiveRadio;
	protected JRadioButton _replaceArchiveRadio;

	/**
	 * Constructor.
	 * 
	 * @param owner The parent window for this dialog
	 * @param archiveType A string specifying the archive type
	 *                    ("Yahoo", "MSN", etc.)
	 */
	public ImportDialog(Window owner, String archiveType)
	{
		super(owner, archiveType+" Import", Dialog.DEFAULT_MODALITY_TYPE);
		this._archiveType = archiveType;
		
		this._initUI();
		this.reset();
	}
	
	/**
	 * Resets the form to the initial state.
	 */
	public void reset()
	{
		this._switchPage(PAGE_LOCATE_ARCHIVE);
		this.archive = null;
		this._unlockControls();
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
		
		// Add listeners
		
		this.addWindowListener(
			new WindowAdapter()
			{
				public void windowClosing(WindowEvent w)
				{
					_onClose();
				}
			}
		);
		
		this._archiveFolderField.getDocument().addDocumentListener(new ArchiveFolderFieldListener());
	}
	
	/**
	 * Initializes the top panel and all child controls.
	 * 
	 * @return A newly created JPanel
	 */
	protected JPanel _createTopPanel()
	{
		this._phaseLabel = new JLabel();
		
		Box panel = new Box(BoxLayout.X_AXIS);
		panel.setOpaque(true);
		panel.setBackground(Color.WHITE);
		panel.add(this._phaseLabel);
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
		
		this._backButton = UIUtils.makeButton("Back", "back", this._cmdButtonListener, COMMAND_GO_BACK);
		this._proceedButton = UIUtils.makeButton("Proceed", "accept", this._cmdButtonListener, COMMAND_PROCEED);
		JButton cancelButton = UIUtils.makeButton("Cancel", "cancel", this._cmdButtonListener, COMMAND_CANCEL);
		UIUtils.copyButtonWidth(this._backButton, this._proceedButton);
		
		panel.add(Box.createHorizontalGlue());
		panel.add(this._backButton);
		panel.add(this._proceedButton);
		panel.add(Box.createHorizontalStrut(8));
		panel.add(cancelButton);
		panel.setBorder(new EmptyBorder(8,8,8,8));
		
		JPanel superPanel = new JPanel(new BorderLayout());
		superPanel.add(new HorizEtchedLine(), BorderLayout.NORTH);
		superPanel.add(panel, BorderLayout.CENTER);
		
		return superPanel;
	}
	
	/**
	 * Initializes the main panel and all child controls.
	 * 
	 * @return A newly created JPanel
	 */
	protected JPanel _createMainPanel()
	{
		this._mainPanel = new JPanel(new CardLayout());
		this._mainPanel.add(this._createLocateArchivePage(), PAGE_LOCATE_ARCHIVE);
		this._mainPanel.add(this._createImportProgressPage(), PAGE_IMPORT_PROGRESS);
		this._mainPanel.setBorder(new EmptyBorder(8,8,8,8));
		
		return this._mainPanel;
	}
	
	/**
	 * Creates the "locate archive folder" page panel
	 * 
	 * @return A newly created JPanel
	 */
	protected JPanel _createLocateArchivePage()
	{
		JLabel helpLabel = new JLabel("<html>"+this._getLocateArchiveHelpText()+"</html>");
		this._archiveFolderField = new JTextField();
		JButton browseButton = UIUtils.makeButton("Browse...", null, this._cmdButtonListener, COMMAND_BROWSE_ARCHIVE_FOLDER);
		
		GridBagPanel page = new GridBagPanel();
		page.add(helpLabel                    , 0, 0, 1, 1, 1.0, 0.0, page.CENTER, page.HORIZ, 0, 0, 0, 8);
		page.add(new JLabel("Archive folder:"), 0, 1, 1, 1, 1.0, 0.0, page.WEST,   page.NONE);
		page.add(this._archiveFolderField,      0, 2, 1, 1, 1.0, 0.0, page.CENTER, page.HORIZ, 0, 0, 0, 8);
		page.add(browseButton,                  0, 3, 1, 1, 1.0, 0.0, page.EAST,   page.NONE);
		
		this._archiveFolderChooser = new JFileChooser();
		this._archiveFolderChooser.setDialogTitle("Select Archive Folder");
		this._archiveFolderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		this._archiveFolderChooser.setMultiSelectionEnabled(false);
				
		return page;
	}
	
	/**
	 * Creates the "import progress" page panel
	 * 
	 * @return A newly created JPanel
	 */
	protected JPanel _createImportProgressPage()
	{
		this._progressLabel1 = new JLabel();
		this._progressLabel2 = new JLabel();
		this._progressBar = new JProgressBar();
		
		GridBagPanel page = new GridBagPanel();
		page.add(this._progressLabel1, 0, 0, 1, 1, 1.0, 0.0, page.WEST,   page.NONE, 0, 0, 0, 8);
		page.add(this._progressLabel2, 1, 0, 1, 1, 0.0, 0.0, page.EAST,   page.NONE, 0, 0, 0, 8);
		page.add(this._progressBar,    0, 1, 2, 1, 1.0, 0.0, page.CENTER, page.HORIZ);
		
		return page;
	}
	
	/**
	 * Disables user interaction in this dialog (useful for
	 * short periods in which the import job is running, but
	 * no progress events will be generated and the import
	 * progress screen is not shown).
	 */
	protected void _lockControls()
	{
		this._locked = true;
		this._runButtonEnableLogic();
		this.setEnabled(false);
	}
	
	/**
	 * Reenables user interaction in this dialog.
	 */
	protected void _unlockControls()
	{
		this._locked = false;
		this._runButtonEnableLogic();
		this.setEnabled(true);
	}
	
	/**
	 * Switches the dialog to a new page.
	 * 
	 * @param newPage A PAGE_ identifier indicating the new page
	 */
	protected void _switchPage(String newPage)
	{
		CardLayout cl = (CardLayout)this._mainPanel.getLayout();
		
		this._currentPage = newPage;
		
		this._phaseLabel.setText("<html><b>"+this._archiveType+" Archive Import</b><br><br>"+
				this._getPhaseDescription()+"</html>");
		
		cl.show(this._mainPanel, newPage);
		this._runButtonEnableLogic();
	}
	
	/**
	 * Gets the help text for the "locate archive" page. This may
	 * have to be personalized for each individual archive type.
	 * 
	 * @return The help text (in HTML format)
	 */
	protected String _getLocateArchiveHelpText()
	{
		return "Please select the location of the "+this._archiveType+" archive directory.";
	}
	
	/**
	 * Gets a description of the current phase for use in
	 * the topmost white ribbon in the dialog.
	 * 
	 * @return A description of the current phase
	 */
	protected String _getPhaseDescription()
	{
		if (this._currentPage.equals(PAGE_LOCATE_ARCHIVE)) return "Specify the "+this._archiveType+" archive folder";
		if (this._currentPage.equals(PAGE_IMPORT_PROGRESS)) return "Import in progress, please wait";
		
		return "User input is required";
	}
	
	/**
	 * Enables or disables the buttons according to
	 * the circumstances.
	 */
	protected void _runButtonEnableLogic()
	{
		this._proceedButton.setEnabled(!this._locked && this._shouldEnableProceed());
		this._backButton.setEnabled(!this._locked && this._shouldEnableBack());
	}
	
	/**
	 * Checks whether the 'proceed' button should be enabled
	 * given the current situation.
	 * 
	 * @return True if the button is to be enabled, false otherwise
	 */
	protected boolean _shouldEnableProceed()
	{
		if (this._currentPage.equals(PAGE_LOCATE_ARCHIVE))
			return (!this._archiveFolderField.getText().equals(""));
		if (this._currentPage.equals(PAGE_IMPORT_PROGRESS)) return false;
		
		return true;	
	}
	
	/**
	 * Checks whether the 'back' button should be enabled
	 * given the current situation.
	 * 
	 * @return True if the button is to be enabled, false otherwise
	 */
	protected boolean _shouldEnableBack()
	{
		if (this._currentPage.equals(PAGE_LOCATE_ARCHIVE)) return false;
		if (this._currentPage.equals(PAGE_IMPORT_PROGRESS)) return false;
		
		return true;
	}
	
	/**
	 * Convenience function for showing a modal error message.
	 */
	protected void _showErrorMessage(String message)
	{
		JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
	}
	
	/**
	 * Executes the "browse archive folder" command.
	 */
	protected void _doBrowseArchiveFolder()
	{
		int status = this._archiveFolderChooser.showOpenDialog(this);
		
		if (status != JFileChooser.APPROVE_OPTION) return;
		
		this._archiveFolderField.setText(this._archiveFolderChooser.getSelectedFile().getPath());
	}
	
	/**
	 * Executes the "proceed" command.
	 */
	protected void _doProceed()
	{
		if (this._currentPage.equals(PAGE_LOCATE_ARCHIVE))
		{
			File archivePath = new File(this._archiveFolderField.getText());
			
			// Some basic checks
			if (!archivePath.canRead())
			{
				this._showErrorMessage("The specified directory does not exist or is unreadable");
				return;
			}
			
			if (!archivePath.isDirectory())
			{
				this._showErrorMessage("The specified path does not point to a directory");
				return;
			}
			
			// Create and run import job
			this._lockControls();
			this._importJob = this._createImportWorker(archivePath);
			this._importJob.execute();
		}
	}
	
	/**
	 * Executes the "go back" command.
	 */
	protected void _doGoBack()
	{
		this._importJob.answerQuery(Boolean.FALSE);
	}
	
	/**
	 * Reacts to the pressing of a button.
	 * 
	 * @param commandId The command string associated with the button
	 */
	protected void _onCommand(String commandId)
	{
		if (commandId.equals(GaimImportDialog.COMMAND_CANCEL)) { this._onClose(); }
		else if (commandId.equals(GaimImportDialog.COMMAND_PROCEED)) { this._doProceed(); }
		else if (commandId.equals(GaimImportDialog.COMMAND_GO_BACK)) { this._doGoBack(); }
		else if (commandId.equals(GaimImportDialog.COMMAND_BROWSE_ARCHIVE_FOLDER)) { this._doBrowseArchiveFolder(); }
	}
	
	/**
	 * Reacts to the successful completion of the import.
	 */
	protected void _onArchiveImported(IMArchive archive)
	{
		this.archive = archive;
		this._onClose();
	}
	
	/**
	 * Reacts to the completion of the import task.
	 */
	protected void _onImportWorkerFinished()
	{
		if ((this._importJob != null) && (this._importJob.getError() != null))
		{
			this._showErrorMessage(this._importJob.getError().getMessage());
			this._onClose();
		}
	}
	
	/**
	 * Reacts to the closing of the window
	 */
	protected void _onClose()
	{
		this.setVisible(false);
		
		if (this._importJob != null)
		{
			if (!(this._importJob.isDone()))
			{
				this._importJob.cancel(true);
			}
			this._importJob = null;
		}
	}
	
	/**
	 * Reacts to a change in the progress of the import operation
	 * currently running.
	 * 
	 * @param event An event structure describing the progress made
	 */
	protected void _onImportProgress(ProgressEvent event)
	{
		this._progressLabel1.setText(event.comment);
		
		if (event.totalItems > 0)
		{
			this._progressLabel2.setText(""+event.completedItems+" of "+event.totalItems);
			this._progressBar.setIndeterminate(false);
			this._progressBar.setMaximum(event.totalItems);
			this._progressBar.setValue(event.completedItems);
		}
		else
		{
			this._progressLabel2.setText("");
			this._progressBar.setIndeterminate(true);
		}
		
		if (this._currentPage != PAGE_IMPORT_PROGRESS) this._switchPage(PAGE_IMPORT_PROGRESS);
		if (this._locked) this._unlockControls();
	}
	
	/**
	 * Reacts to an import message feedback event issued by the
	 * currently running import job. The action is to pop up a
	 * dialog box with the appropriate icon and items.
	 * 
	 * @param message An object describing the import message
	 */
	protected void _onImportMessage(ImportMessage message)
	{
		String title = "";
		int type = 0;
		
		switch (message.type)
		{
		case INFORMATION: title="Note"; type = JOptionPane.INFORMATION_MESSAGE; break;
		case WARNING: title="Warning"; type = JOptionPane.WARNING_MESSAGE; break;
		case ERROR: title="Error"; type = JOptionPane.ERROR_MESSAGE; break;
		}
		
		JOptionPane.showMessageDialog(this, message.content, title, type);
	}
	
	/**
	 * Reacts to changes in the Archive Folder text field.
	 */
	protected void _onArchiveFolderFieldChange()
	{
		this._runButtonEnableLogic();
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
		throw new RuntimeException("_createImportWorker() not implemented");
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
				_onCommand(evt.getActionCommand());
			}
		}
	}
	
	/**
	 * Internal class that listens to changes in the Archive Folder
	 * field.
	 */
	protected class ArchiveFolderFieldListener implements DocumentListener
	{
		@Override
		public void changedUpdate(DocumentEvent ev)
		{
			_onArchiveFolderFieldChange();
		}

		@Override
		public void insertUpdate(DocumentEvent ev)
		{
			_onArchiveFolderFieldChange();
		}

		@Override
		public void removeUpdate(DocumentEvent arg0)
		{
			_onArchiveFolderFieldChange();	
		}
	}
	
	/**
	 * Internal class for a Swing worker running the import job.
	 */
	protected abstract class ImportWorker extends SwingWorker<Object,Object> implements ProgressListener
	{
		protected Exception _error = null;
		protected Lock _lock = new ReentrantLock();
		protected Condition _queryAnswered = _lock.newCondition();
		protected Object _queryAnswer;
		
		/**
		 * Override this function to specify the actual import
		 * actions. You may call publish, _awaitAnswer and throw
		 * exceptions from here. The procedure must end by
		 * publishing the IMArchive object.
		 * 
		 * This procedure executes in the worker's private thread.
		 */
		protected void _doImport() throws Exception
		{
			throw new RuntimeException("_doImport() not implemented");
		}
		
		/**
		 * Override this function to specify the actions to be
		 * taken upon the receipt of an event (via the publish
		 * function). Note that the receipt of an archive or
		 * a import progress event are handled automatically
		 * and do not involve this function
		 * 
		 * @param event The event to be processed
		 */
		protected void _processEvent(Object event)
		{
			
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
			try { _doImport(); } catch (Exception e) { this._error = e; }
			
			return null;
		}
		
		@Override
		/**
		 * Executes GUI operations at the end of this worker's task.
		 * 
		 * This procedure executes in the Swing event dispatch thread. 
		 */
		protected void done()
		{
			_onImportWorkerFinished();
		}

		@Override
		/**
		 * Reacts to events published by the import job.
		 * 
		 * This procedure executes in the Swing event dispatch thread.
		 */
		protected void process(List<Object> events)
		{
			for (Object event : events)
			{
				if (event instanceof ProgressEvent)
				{
					_onImportProgress((ProgressEvent)event);
				}
				else if (event instanceof ImportMessage)
				{
					_onImportMessage((ImportMessage)event);
				}
				else if (event instanceof IMArchive)
				{
					_onArchiveImported((IMArchive)event);
				}
				else this._processEvent(event);
			}
		}
		
		/**
		 * Returns the fatal error that ended this import, if any.
		 * 
		 * @return An Exception object that describes the error,
		 *         or null if the import completed successfully.
		 */
		public Exception getError()
		{
			return this._error;
		}

		@Override
		/**
		 * Reacts to progress in the import.
		 * 
		 * This procedure executes in the worker's private
		 * thread.
		 */
		public void onProgress(ProgressEvent ev)
		{
			this.publish(ev);	
		}
		
		/**
		 * Blocks the thread until the user has supplied an answer
		 * to the last query.
		 * 
		 * @return The answer supplied by the user (or null if the
		 *         thread was interrupted)
		 */
		protected Object _awaitAnswer()
		{
			this._lock.lock();
			try
			{
				this._queryAnswered.await();
			}
			catch (InterruptedException e)
			{
				this._queryAnswer = null;
			}
			finally
			{
				this._lock.unlock();
			}
			
			return this._queryAnswer;
		}
		
		/**
		 * Answers the last query.
		 * 
		 * @param Object answer The answer supplied
		 */
		public void answerQuery(Object answer)
		{
			this._lock.lock();
			try
			{
				this._queryAnswer = answer;
				this._queryAnswered.signal();
			}
			finally
			{
				this._lock.unlock();
			}
		}
	}
}
