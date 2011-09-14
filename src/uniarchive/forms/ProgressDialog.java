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

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.border.EmptyBorder;

import uniarchive.models.ProgressEvent;
import uniarchive.widgets.GridBagPanel;

/**
 * Class for a simple operation progress dialog.
 */
public class ProgressDialog extends JDialog
{
	private static final long serialVersionUID = 1;
	
	protected JLabel _progressLabel;
	protected JProgressBar _progressBar;
	protected int _hiddenness = 1;
	
	/**
	 * Constructor.
	 * 
	 * @param owner The parent window for this dialog
	 */
	public ProgressDialog(Window owner)
	{
		super(owner, "Operation Progress", Dialog.DEFAULT_MODALITY_TYPE);
		
		this._initUI();
	}
	
	/**
	 * Updates the progress information displayed in
	 * the dialog.
	 * 
	 * @param progress A progress event containing the
	 *                 current progress info
	 */
	public void setProgress(ProgressEvent progress)
	{
		this._progressLabel.setText(progress.comment);
		if (progress.totalItems > 0)
		{
			this._progressBar.setMaximum(progress.totalItems);
			this._progressBar.setValue(progress.completedItems);
			this._progressBar.setIndeterminate(false);
		}
		else
		{
			this._progressBar.setIndeterminate(true);
		}
	}
	
	/**
	 * Shows the progress dialog. You should
	 * call this instead of setVisible as it features
	 * a mechanism for resolving a possible race conditions
	 * for tasks that complete before the dialog is shown.
	 */
	public void popup()
	{
		this._hiddenness--;
		if (this._hiddenness <= 0) this.setVisible(true);
	}
	
	/**
	 * Hides the progress dialog. You should
	 * call this instead of setVisible as it features
	 * a mechanism for resolving a possible race conditions
	 * for tasks that complete before the dialog is shown.
	 */
	public void close()
	{
		this._hiddenness++;
		if (this._hiddenness > 0) this.setVisible(false);
	}
	
	/**
	 * Initializes the form GUI.
	 */
	protected void _initUI()
	{
		this.setLayout(new BorderLayout());
		
		this.add(this._createMainPanel(), BorderLayout.CENTER);
		
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
		this._progressLabel = new JLabel("Operation in progress...");
		this._progressBar = new JProgressBar();
		
		GridBagPanel mainPanel = new GridBagPanel();
		mainPanel.add(this._progressLabel, 0, 0, 1, 1, 1.0, 0.0, mainPanel.WEST,   mainPanel.NONE, 0, 0, 0, 8);
		mainPanel.add(this._progressBar,    0, 1, 1, 1, 1.0, 0.0, mainPanel.CENTER, mainPanel.HORIZ);
		mainPanel.setBorder(new EmptyBorder(8,8,8,8));
		
		mainPanel.setMinimumSize(new Dimension(this._progressLabel.getFont().getSize()*40, 0));
		mainPanel.setPreferredSize(new Dimension(this._progressLabel.getFont().getSize()*40, mainPanel.getPreferredSize().height));
		
		return mainPanel;
	}
}
