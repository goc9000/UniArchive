/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.forms.importing;

import java.awt.Window;
import java.io.File;

import uniarchive.models.archive.IMArchive;
import uniarchive.models.yahoo_import.YahooImportJob;

/**
 * Class for the Yahoo Import wizard dialog.
 */
public class YahooImportDialog extends ImportDialog
{
	private static final long serialVersionUID = 1;
	
	/**
	 * Constructor.
	 * 
	 * @param owner The parent window for this dialog
	 */
	public YahooImportDialog(Window owner)
	{
		super(owner, "Yahoo! Messenger");
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
		return "Please select the location of the Yahoo! Messenger archive directory. This "+
			"folder is usually named Archive and contains the subfolders Conferences and Messages.";
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
		return new YahooImportWorker(archivePath);
	}
	
	/**
	 * Internal class for a Swing worker running the import job.
	 */
	protected class YahooImportWorker extends ImportWorker
	{
		protected YahooImportJob _job;
		
		/**
		 * Constructor.
		 * 
		 * @param archivePath The path to the archive 
		 */
		public YahooImportWorker(File archivePath)
		{
			this._job = new YahooImportJob(archivePath, this);
		}
		
		/**
		 * Override this function to specify the actual import
		 * actions. You may call publish, _awaitAnswer and throw
		 * exceptions from here. The procedure must end by
		 * publishing the IMArchive object.
		 * 
		 * This procedure executes in the worker's private thread.
		 */
		@Override
		protected void _doImport() throws Exception
		{
			IMArchive archive = this._job.run();
			this.publish(archive);
		}
	}
}
