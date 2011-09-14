/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive;

import javax.swing.JOptionPane;
import javax.swing.UIManager;

import uniarchive.forms.MainForm;
import uniarchive.graphics.IconManager;
import uniarchive.graphics.SmileyManager;
import uniarchive.models.archive.ArchiveDb;

/**
 * Main class.
 */
public abstract class Main
{
	public static String[] programArgs = null;
	
	/**
	 * Main program.
	 * 
	 * @param args The program's command-line arguments
	 */
	public static void main(String[] args) throws Exception
	{
		Main.programArgs = args;
		
		// Initialize the GUI in the Swing event thread	
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			public void run()
			{
				Main._initApp();
			}
		});		
	}

	/**
	 * Initializes the application. This executes in the Swing thread
	 * so we can initialize the GUI and show messages if something
	 * goes wrong.
	 */
	protected static void _initApp()
	{	
		try
		{
			// Set the system-specific look-and-feel instead of the ugly Java defaults
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			
			// Initialize singletons
			ArchiveDb.getInstance();
			IconManager.getInstance();
			SmileyManager.getInstance();
			
			MainForm mainForm = new MainForm();
			mainForm.setVisible(true);
		}
		catch (Exception e)
		{
			JOptionPane.showMessageDialog(null, "An error occured while intializing the aplication:\n"+e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
		}		
	}
}
