/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.widgets;

import java.awt.Font;

import javax.swing.JLabel;

import uniarchive.graphics.IconManager;

/**
 * Class for a special label that is usually placed
 * on top of view components.
 */
public class ViewLabel extends JLabel
{
	private static final long serialVersionUID = 1L;

	/**
	 * Constructor.
	 * 
	 * @param caption The label's caption
	 */
	public ViewLabel(String caption)
	{
		super(caption);
		
		this.setFont(this.getFont().deriveFont(Font.BOLD));
		this.setHorizontalTextPosition(JLabel.LEFT);
		this.setIcon(IconManager.getInstance().getIcon("kibble/view_arrow"));
	}
}
